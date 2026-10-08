package com.sibang.hankki.reservation.application;

import com.sibang.hankki.reservation.application.exception.InvalidReservationRequestException;
import com.sibang.hankki.reservation.application.exception.ReservationBookingSettingsNotConfiguredException;
import com.sibang.hankki.reservation.application.exception.ReservationCapacityUnavailableException;
import com.sibang.hankki.reservation.application.exception.ReservationIdempotencyConflictException;
import com.sibang.hankki.reservation.application.exception.ReservationNotFoundException;
import com.sibang.hankki.reservation.application.exception.ReservationSlotNotFoundException;
import com.sibang.hankki.reservation.application.port.in.CreateReservationCommand;
import com.sibang.hankki.reservation.application.port.in.CreateReservationResult;
import com.sibang.hankki.reservation.application.port.in.CreateReservationUseCase;
import com.sibang.hankki.reservation.application.port.out.ReservationEventPersistencePort;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort;
import com.sibang.hankki.reservation.domain.model.Reservation;
import com.sibang.hankki.reservation.domain.model.ReservationEvent;
import com.sibang.hankki.reservation.domain.model.ReservationEventType;
import com.sibang.hankki.reservation.domain.model.ReservationStatus;
import com.sibang.hankki.reservation.domain.model.VisitStatus;
import com.sibang.hankki.restaurant.application.BookingDomainMapper;
import com.sibang.hankki.restaurant.application.port.out.BookingSettings;
import com.sibang.hankki.restaurant.application.port.out.BookingSettingsPort;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotData;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantData;
import com.sibang.hankki.restaurant.domain.booking.BookingPolicy;
import com.sibang.hankki.restaurant.domain.booking.BookingRuleViolationException;
import com.sibang.hankki.restaurant.domain.booking.BookingTime;
import com.sibang.hankki.user.application.exception.CustomerProfileNotFoundException;
import com.sibang.hankki.user.application.port.in.CustomerProfileUseCase;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateReservationService implements CreateReservationUseCase {

    private final RestaurantCatalogPort restaurantCatalogPort;
    private final BookingSettingsPort bookingSettingsPort;
    private final BookingSlotPort bookingSlotPort;
    private final ReservationPersistencePort reservationPersistencePort;
    private final ReservationEventPersistencePort reservationEventPersistencePort;
    private final CustomerProfileUseCase customerProfileUseCase;
    private final Clock clock;

    public CreateReservationService(
            RestaurantCatalogPort restaurantCatalogPort,
            BookingSettingsPort bookingSettingsPort,
            BookingSlotPort bookingSlotPort,
            ReservationPersistencePort reservationPersistencePort,
            ReservationEventPersistencePort reservationEventPersistencePort,
            CustomerProfileUseCase customerProfileUseCase,
            Clock clock) {
        this.restaurantCatalogPort = restaurantCatalogPort;
        this.bookingSettingsPort = bookingSettingsPort;
        this.bookingSlotPort = bookingSlotPort;
        this.reservationPersistencePort = reservationPersistencePort;
        this.reservationEventPersistencePort = reservationEventPersistencePort;
        this.customerProfileUseCase = customerProfileUseCase;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CreateReservationResult create(CreateReservationCommand command) {
        return create(command, null);
    }

    @Override
    @Transactional
    public CreateReservationResult create(CreateReservationCommand command, UUID authenticatedCustomerId) {
        Objects.requireNonNull(command, "command is required");
        LocalDate date = parseDate(command.date());
        LocalTime time = parseTime(command.time());
        validateRequired(command);
        UUID customerId = activeCustomerId(authenticatedCustomerId);
        String fingerprint = fingerprint(command, customerId);

        reservationPersistencePort.lockIdempotencyKey(command.idempotencyKey());
        Reservation previous = reservationPersistencePort.findByIdempotencyKey(command.idempotencyKey()).orElse(null);
        if (previous != null) {
            if (!previous.requestFingerprint().equals(fingerprint)) {
                throw new ReservationIdempotencyConflictException();
            }
            return new CreateReservationResult(
                    previous,
                    command.restaurantSlug(),
                    date,
                    time,
                    previous.status() == ReservationStatus.PENDING,
                    true,
                    null);
        }

        LocalDate today = LocalDate.now(clock.withZone(BookingTime.RESTAURANT_TIME_ZONE));
        validateBasicRequest(date, today, command.partySize());
        RestaurantData restaurant = restaurantCatalogPort.findActiveRestaurantBySlug(command.restaurantSlug())
                .orElseThrow(() -> new ReservationNotFoundException(command.restaurantSlug()));
        BookingSettings settings = bookingSettingsPort.findByRestaurantId(restaurant.id())
                .orElseThrow(() -> new ReservationBookingSettingsNotConfiguredException(restaurant.slug()));
        BookingPolicy policy = BookingDomainMapper.policy(settings);
        validatePolicy(policy, date, today, command.partySize());
        if (!policy.supportsOnlineAvailability(command.partySize())) {
            throw new InvalidReservationRequestException("partySize exceeds the maximum online party size");
        }

        Instant startsAt = date.atTime(time).atZone(BookingTime.RESTAURANT_TIME_ZONE).toInstant();
        if (!startsAt.isAfter(Instant.now(clock))) {
            throw new InvalidReservationRequestException("Reservation time must be in the future");
        }
        BookingSlotData slot = bookingSlotPort.findByRestaurantIdAndStartsAt(restaurant.id(), startsAt)
                .orElseThrow(ReservationSlotNotFoundException::new);
        boolean requiresConfirmation = policy.requiresRestaurantConfirmation(command.partySize());
        ReservationStatus status = requiresConfirmation ? ReservationStatus.PENDING : ReservationStatus.CONFIRMED;
        if (status == ReservationStatus.CONFIRMED
                && !bookingSlotPort.reserveCapacity(slot.id(), command.partySize())) {
            throw new ReservationCapacityUnavailableException();
        }

        Instant now = Instant.now(clock);
        String managementToken = customerId == null ? ReservationToken.generate() : null;
        Reservation reservation = new Reservation(
                UUID.randomUUID(),
                "SHK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 28).toUpperCase(),
                command.idempotencyKey(),
                fingerprint,
                restaurant.id(),
                slot.id(),
                customerId,
                command.customerName(),
                command.customerEmail(),
                command.customerPhone(),
                startsAt,
                slot.endsAt(),
                command.partySize(),
                status,
                false,
                status == ReservationStatus.CONFIRMED ? VisitStatus.EXPECTED : null,
                command.specialRequest(),
                command.preOrderNote(),
                managementToken == null ? null : ReservationToken.hash(managementToken),
                null,
                null,
                null,
                0,
                now,
                now);
        Reservation saved = reservationPersistencePort.save(reservation);
        appendEvent(saved, ReservationEventType.REQUESTED, fingerprint, now);
        if (status == ReservationStatus.CONFIRMED) {
            appendEvent(saved, ReservationEventType.CONFIRMED, fingerprint, now.plusNanos(1_000));
        }
        return new CreateReservationResult(
                saved, restaurant.slug(), date, time, requiresConfirmation, false, managementToken);
    }

    private void appendEvent(Reservation reservation, ReservationEventType type, String fingerprint, Instant now) {
        reservationEventPersistencePort.append(new ReservationEvent(
                UUID.randomUUID(), reservation.id(), type, null, null, fingerprint, null, now));
    }

    private void validateRequired(CreateReservationCommand command) {
        if (isBlank(command.idempotencyKey()) || command.idempotencyKey().length() > 255) {
            throw new InvalidReservationRequestException("Idempotency-Key is required and must be at most 255 characters");
        }
        if (isBlank(command.restaurantSlug()) || isBlank(command.customerName()) || isBlank(command.customerPhone())) {
            throw new InvalidReservationRequestException("restaurantSlug, customerName and customerPhone are required");
        }
        if (command.customerName().length() > 120 || command.customerPhone().length() > 30
                || (command.customerEmail() != null && command.customerEmail().length() > 320)) {
            throw new InvalidReservationRequestException("customer input exceeds the supported length");
        }
    }

    private void validateBasicRequest(LocalDate date, LocalDate today, int partySize) {
        try {
            BookingPolicy.validateBasicRequest(date, today, partySize);
        } catch (BookingRuleViolationException exception) {
            throw new InvalidReservationRequestException(exception.getMessage());
        }
    }

    private void validatePolicy(BookingPolicy policy, LocalDate date, LocalDate today, int partySize) {
        try {
            policy.validateAvailabilityRequest(date, today, partySize);
        } catch (BookingRuleViolationException exception) {
            throw new InvalidReservationRequestException(exception.getMessage());
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new InvalidReservationRequestException("date must be ISO YYYY-MM-DD");
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw new InvalidReservationRequestException("date must be ISO YYYY-MM-DD");
        }
    }

    private LocalTime parseTime(String value) {
        if (value == null || !value.matches("\\d{2}:\\d{2}")) {
            throw new InvalidReservationRequestException("time must be HH:mm");
        }
        try {
            return LocalTime.parse(value);
        } catch (RuntimeException exception) {
            throw new InvalidReservationRequestException("time must be HH:mm");
        }
    }

    private UUID activeCustomerId(UUID customerId) {
        if (customerId == null) {
            return null;
        }
        try {
            customerProfileUseCase.get(customerId);
            return customerId;
        } catch (CustomerProfileNotFoundException exception) {
            return null;
        }
    }

    private String fingerprint(CreateReservationCommand command, UUID customerId) {
        String request = value(command.restaurantSlug()) + value(command.date()) + value(command.time())
                + command.partySize() + value(command.customerName()) + value(command.customerPhone())
                + value(command.customerEmail()) + value(command.specialRequest()) + value(command.preOrderNote())
                + value(customerId == null ? null : customerId.toString());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(request.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String value(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
