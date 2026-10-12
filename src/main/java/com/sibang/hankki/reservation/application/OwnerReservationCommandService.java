package com.sibang.hankki.reservation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sibang.hankki.reservation.application.exception.InvalidReservationRequestException;
import com.sibang.hankki.reservation.application.exception.InvalidReservationStateException;
import com.sibang.hankki.reservation.application.exception.NoActiveBookingSlotException;
import com.sibang.hankki.reservation.application.exception.ReservationCapacityUnavailableException;
import com.sibang.hankki.reservation.application.exception.ReservationIdempotencyConflictException;
import com.sibang.hankki.reservation.application.exception.ReservationNotFoundException;
import com.sibang.hankki.reservation.application.port.in.OwnerReservationCommandUseCase;
import com.sibang.hankki.reservation.application.port.in.OwnerReservationCommandUseCase.OwnerWalkInCommand;
import com.sibang.hankki.reservation.application.port.in.OwnerReservationCommandUseCase.OwnerWalkInResult;
import com.sibang.hankki.reservation.application.port.out.ReservationEventPersistencePort;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort;
import com.sibang.hankki.reservation.domain.model.Reservation;
import com.sibang.hankki.reservation.domain.model.ReservationEvent;
import com.sibang.hankki.reservation.domain.model.ReservationEventType;
import com.sibang.hankki.reservation.domain.model.ReservationStatus;
import com.sibang.hankki.reservation.domain.model.VisitStatus;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerReservationCommandService implements OwnerReservationCommandUseCase {

    private final ReservationPersistencePort reservationPersistencePort;
    private final ReservationEventPersistencePort eventPersistencePort;
    private final BookingSlotPort bookingSlotPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OwnerReservationCommandService(
            ReservationPersistencePort reservationPersistencePort,
            ReservationEventPersistencePort eventPersistencePort,
            BookingSlotPort bookingSlotPort,
            ObjectMapper objectMapper,
            Clock clock) {
        this.reservationPersistencePort = reservationPersistencePort;
        this.eventPersistencePort = eventPersistencePort;
        this.bookingSlotPort = bookingSlotPort;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public OwnerWalkInResult createWalkIn(OwnerWalkInCommand command) {
        String customerName = required(command.customerName(), "customerName", 120);
        String customerPhone = required(command.customerPhone(), "customerPhone", 30);
        String specialRequest = optional(command.specialRequest());
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length() > 255) {
            throw new InvalidReservationRequestException(
                    "Idempotency-Key is required and must be at most 255 characters");
        }
        if (command.partySize() <= 0) {
            throw new InvalidReservationRequestException("partySize must be positive");
        }

        String fingerprint = walkInFingerprint(
                command.restaurantId(), customerName, customerPhone, command.partySize(), specialRequest);
        reservationPersistencePort.lockIdempotencyKey(command.idempotencyKey());
        Reservation previous = reservationPersistencePort
                .findByIdempotencyKey(command.idempotencyKey()).orElse(null);
        if (previous != null) {
            if (!previous.restaurantId().equals(command.restaurantId())
                    || !previous.requestFingerprint().equals(fingerprint)) {
                throw new ReservationIdempotencyConflictException();
            }
            return new OwnerWalkInResult(previous, true);
        }

        Instant now = Instant.now(clock);
        var slot = bookingSlotPort.findLatestActive(command.restaurantId(), now)
                .orElseThrow(NoActiveBookingSlotException::new);
        if (!bookingSlotPort.reserveCapacity(slot.id(), command.partySize())) {
            throw new ReservationCapacityUnavailableException();
        }

        Reservation walkIn = reservationPersistencePort.save(new Reservation(
                UUID.randomUUID(),
                CreateReservationService.newReference(),
                command.idempotencyKey(),
                fingerprint,
                command.restaurantId(),
                slot.id(),
                null,
                customerName,
                null,
                customerPhone,
                slot.startsAt(),
                slot.endsAt(),
                command.partySize(),
                ReservationStatus.CONFIRMED,
                false,
                VisitStatus.ARRIVED,
                specialRequest,
                null,
                null,
                null,
                now,
                command.actorUserId(),
                0,
                now,
                now));
        appendEvent(walkIn, ReservationEventType.REQUESTED, command.actorUserId(), null, now);
        appendEvent(walkIn, ReservationEventType.CONFIRMED, command.actorUserId(), null, now.plusNanos(1_000));
        appendEvent(walkIn, ReservationEventType.CHECKED_IN, command.actorUserId(), null, now.plusNanos(2_000));
        return new OwnerWalkInResult(walkIn, false);
    }

    @Override
    @Transactional
    public Reservation confirm(UUID reservationId, UUID restaurantId, UUID actorUserId) {
        Reservation reservation = lock(reservationId, restaurantId);
        if (reservation.status() == ReservationStatus.CONFIRMED) {
            return reservation;
        }
        requirePending(reservation);
        if (reservation.bookingSlotId() == null
                || !bookingSlotPort.lockByIdAndRestaurantId(reservation.bookingSlotId(), restaurantId)) {
            throw new InvalidReservationStateException("Reservation has no matching booking slot");
        }
        if (!bookingSlotPort.reserveCapacity(reservation.bookingSlotId(), reservation.partySize())) {
            throw new ReservationCapacityUnavailableException();
        }

        Reservation confirmed = reservationPersistencePort.save(transition(
                reservation, ReservationStatus.CONFIRMED, VisitStatus.EXPECTED));
        appendEvent(confirmed, ReservationEventType.CONFIRMED, actorUserId, null);
        return confirmed;
    }

    @Override
    @Transactional
    public Reservation decline(UUID reservationId, UUID restaurantId, UUID actorUserId, String reason) {
        Reservation reservation = lock(reservationId, restaurantId);
        if (reservation.status() == ReservationStatus.DECLINED) {
            return reservation;
        }
        requirePending(reservation);

        Reservation declined = reservationPersistencePort.save(transition(
                reservation, ReservationStatus.DECLINED, null));
        appendEvent(declined, ReservationEventType.DECLINED, actorUserId, reasonMetadata(reason));
        return declined;
    }

    @Override
    @Transactional
    public Reservation cancel(UUID reservationId, UUID restaurantId, UUID actorUserId, String reason) {
        String cancellationReason = normalizeCancellationReason(reason);
        Reservation reservation = lock(reservationId, restaurantId);
        if (reservation.status() == ReservationStatus.CANCELLED) {
            return reservation;
        }
        if (reservation.status() != ReservationStatus.CONFIRMED
                || reservation.visitStatus() != VisitStatus.EXPECTED) {
            throw new InvalidReservationStateException(
                    "Reservation must be CONFIRMED and EXPECTED for cancellation");
        }
        if (reservation.bookingSlotId() == null
                || !bookingSlotPort.lockByIdAndRestaurantId(reservation.bookingSlotId(), restaurantId)) {
            throw new InvalidReservationStateException("Reservation has no matching booking slot");
        }
        if (!bookingSlotPort.releaseCapacity(reservation.bookingSlotId(), reservation.partySize())) {
            throw new InvalidReservationStateException("Reserved capacity could not be released");
        }

        Reservation cancelled = reservationPersistencePort.save(cancelled(reservation));
        appendEvent(cancelled, ReservationEventType.CANCELLED_BY_RESTAURANT,
                actorUserId, reasonMetadata(cancellationReason));
        return cancelled;
    }

    @Override
    @Transactional
    public Reservation checkIn(String checkInToken, UUID restaurantId, UUID actorUserId) {
        if (checkInToken == null || checkInToken.isBlank()) {
            throw new ReservationNotFoundException();
        }
        Reservation reservation = reservationPersistencePort
                .findByCheckInTokenHashAndRestaurantIdForUpdate(ReservationToken.hash(checkInToken), restaurantId)
                .orElseThrow(ReservationNotFoundException::new);
        return checkIn(reservation, actorUserId);
    }

    @Override
    @Transactional
    public Reservation checkIn(UUID reservationId, UUID restaurantId, UUID actorUserId) {
        return checkIn(lock(reservationId, restaurantId), actorUserId);
    }

    private Reservation checkIn(Reservation reservation, UUID actorUserId) {
        if (reservation.status() == ReservationStatus.CONFIRMED
                && reservation.visitStatus() == VisitStatus.ARRIVED) {
            return reservation;
        }
        if (reservation.status() != ReservationStatus.CONFIRMED
                || reservation.visitStatus() != VisitStatus.EXPECTED) {
            throw new InvalidReservationStateException(
                    "Reservation must be CONFIRMED and EXPECTED for check-in");
        }

        Instant now = Instant.now(clock);
        Reservation checkedIn = reservationPersistencePort.save(checkedIn(reservation, actorUserId, now));
        appendEvent(checkedIn, ReservationEventType.CHECKED_IN, actorUserId, null);
        return checkedIn;
    }

    @Override
    @Transactional
    public Reservation seat(UUID reservationId, UUID restaurantId, UUID actorUserId) {
        return transitionVisit(
                reservationId, restaurantId, actorUserId,
                VisitStatus.ARRIVED, VisitStatus.SEATED, ReservationEventType.SEATED);
    }

    @Override
    @Transactional
    public Reservation complete(UUID reservationId, UUID restaurantId, UUID actorUserId) {
        return transitionVisit(
                reservationId, restaurantId, actorUserId,
                VisitStatus.SEATED, VisitStatus.COMPLETED, ReservationEventType.COMPLETED);
    }

    private Reservation transitionVisit(
            UUID reservationId,
            UUID restaurantId,
            UUID actorUserId,
            VisitStatus requiredVisitStatus,
            VisitStatus targetVisitStatus,
            ReservationEventType eventType) {
        Reservation reservation = lock(reservationId, restaurantId);
        if (reservation.status() == ReservationStatus.CONFIRMED
                && reservation.visitStatus() == targetVisitStatus) {
            return reservation;
        }
        if (reservation.status() != ReservationStatus.CONFIRMED
                || reservation.visitStatus() != requiredVisitStatus) {
            throw new InvalidReservationStateException(
                    "Reservation must be CONFIRMED and " + requiredVisitStatus + " for this transition");
        }

        Reservation updated = reservationPersistencePort.save(
                transition(reservation, ReservationStatus.CONFIRMED, targetVisitStatus));
        appendEvent(updated, eventType, actorUserId, null);
        return updated;
    }

    private Reservation lock(UUID reservationId, UUID restaurantId) {
        return reservationPersistencePort.findByIdAndRestaurantIdForUpdate(reservationId, restaurantId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }

    private void requirePending(Reservation reservation) {
        if (reservation.status() != ReservationStatus.PENDING) {
            throw new InvalidReservationStateException(
                    "Reservation must be PENDING for this transition");
        }
    }

    private Reservation transition(
            Reservation reservation, ReservationStatus status, VisitStatus visitStatus) {
        return new Reservation(
                reservation.id(), reservation.reference(), reservation.idempotencyKey(), reservation.requestFingerprint(),
                reservation.restaurantId(), reservation.bookingSlotId(), reservation.customerId(), reservation.customerName(),
                reservation.customerEmail(), reservation.customerPhone(), reservation.startsAt(), reservation.endsAt(),
                reservation.partySize(), status, reservation.capacityOverride(), visitStatus,
                reservation.specialRequest(), reservation.preOrderNote(), reservation.managementTokenHash(),
                reservation.checkInTokenHash(),
                reservation.checkedInAt(), reservation.checkedInBy(), reservation.version(),
                reservation.createdAt(), reservation.updatedAt());
    }

    private Reservation checkedIn(Reservation reservation, UUID actorUserId, Instant checkedInAt) {
        return new Reservation(
                reservation.id(), reservation.reference(), reservation.idempotencyKey(), reservation.requestFingerprint(),
                reservation.restaurantId(), reservation.bookingSlotId(), reservation.customerId(), reservation.customerName(),
                reservation.customerEmail(), reservation.customerPhone(), reservation.startsAt(), reservation.endsAt(),
                reservation.partySize(), reservation.status(), reservation.capacityOverride(), VisitStatus.ARRIVED,
                reservation.specialRequest(), reservation.preOrderNote(), reservation.managementTokenHash(),
                reservation.checkInTokenHash(), checkedInAt, actorUserId, reservation.version(),
                reservation.createdAt(), reservation.updatedAt());
    }

    private Reservation cancelled(Reservation reservation) {
        return new Reservation(
                reservation.id(), reservation.reference(), reservation.idempotencyKey(), reservation.requestFingerprint(),
                reservation.restaurantId(), reservation.bookingSlotId(), reservation.customerId(), reservation.customerName(),
                reservation.customerEmail(), reservation.customerPhone(), reservation.startsAt(), reservation.endsAt(),
                reservation.partySize(), ReservationStatus.CANCELLED, false, null,
                reservation.specialRequest(), reservation.preOrderNote(), reservation.managementTokenHash(),
                reservation.checkInTokenHash(), reservation.checkedInAt(), reservation.checkedInBy(), reservation.version(),
                reservation.createdAt(), reservation.updatedAt());
    }

    private void appendEvent(
            Reservation reservation, ReservationEventType type, UUID actorUserId, String metadata) {
        appendEvent(reservation, type, actorUserId, metadata, Instant.now(clock));
    }

    private void appendEvent(
            Reservation reservation,
            ReservationEventType type,
            UUID actorUserId,
            String metadata,
            Instant createdAt) {
        eventPersistencePort.append(new ReservationEvent(
                UUID.randomUUID(),
                reservation.id(),
                type,
                actorUserId,
                "owner-" + type.name().toLowerCase() + ":" + reservation.id(),
                null,
                metadata,
                createdAt));
    }

    private String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidReservationRequestException(field + " is required");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new InvalidReservationRequestException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String walkInFingerprint(
            UUID restaurantId, String customerName, String customerPhone, int partySize, String specialRequest) {
        return ReservationToken.hash("walk-in" + value(restaurantId.toString()) + value(customerName)
                + value(customerPhone) + partySize + value(specialRequest));
    }

    private String value(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    private String reasonMetadata(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(Map.of("reason", reason));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize reservation reason", exception);
        }
    }

    private String normalizeCancellationReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidReservationRequestException("Cancellation reason is required");
        }
        String normalized = reason.strip();
        if (normalized.length() > 500) {
            throw new InvalidReservationRequestException("Cancellation reason must not exceed 500 characters");
        }
        return normalized;
    }
}
