package com.sibang.hankki.reservation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sibang.hankki.reservation.application.exception.InvalidReservationStateException;
import com.sibang.hankki.reservation.application.exception.ReservationCapacityUnavailableException;
import com.sibang.hankki.reservation.application.exception.ReservationNotFoundException;
import com.sibang.hankki.reservation.application.port.in.OwnerReservationCommandUseCase;
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
                reservation.specialRequest(), reservation.preOrderNote(), reservation.checkInTokenHash(),
                reservation.checkedInAt(), reservation.checkedInBy(), reservation.version(),
                reservation.createdAt(), reservation.updatedAt());
    }

    private void appendEvent(
            Reservation reservation, ReservationEventType type, UUID actorUserId, String metadata) {
        eventPersistencePort.append(new ReservationEvent(
                UUID.randomUUID(),
                reservation.id(),
                type,
                actorUserId,
                "owner-" + type.name().toLowerCase() + ":" + reservation.id(),
                null,
                metadata,
                Instant.now(clock)));
    }

    private String reasonMetadata(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(Map.of("reason", reason));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize decline reason", exception);
        }
    }
}
