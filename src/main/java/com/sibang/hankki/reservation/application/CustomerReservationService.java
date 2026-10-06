package com.sibang.hankki.reservation.application;

import com.sibang.hankki.reservation.application.exception.InvalidReservationStateException;
import com.sibang.hankki.reservation.application.exception.ReservationNotFoundException;
import com.sibang.hankki.reservation.application.port.in.CustomerReservationUseCase;
import com.sibang.hankki.reservation.application.port.out.ReservationEventPersistencePort;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort;
import com.sibang.hankki.reservation.domain.model.Reservation;
import com.sibang.hankki.reservation.domain.model.ReservationEvent;
import com.sibang.hankki.reservation.domain.model.ReservationEventType;
import com.sibang.hankki.reservation.domain.model.ReservationStatus;
import com.sibang.hankki.restaurant.application.port.out.BookingSettings;
import com.sibang.hankki.restaurant.application.port.out.BookingSettingsPort;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerReservationService implements CustomerReservationUseCase {

    private final ReservationPersistencePort reservationPersistencePort;
    private final ReservationEventPersistencePort eventPersistencePort;
    private final BookingSettingsPort bookingSettingsPort;
    private final BookingSlotPort bookingSlotPort;
    private final Clock clock;

    public CustomerReservationService(
            ReservationPersistencePort reservationPersistencePort,
            ReservationEventPersistencePort eventPersistencePort,
            BookingSettingsPort bookingSettingsPort,
            BookingSlotPort bookingSlotPort,
            Clock clock) {
        this.reservationPersistencePort = reservationPersistencePort;
        this.eventPersistencePort = eventPersistencePort;
        this.bookingSettingsPort = bookingSettingsPort;
        this.bookingSlotPort = bookingSlotPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Reservation findById(UUID reservationId, String managementToken) {
        return reservationPersistencePort.findByIdAndManagementTokenHash(
                        reservationId, tokenHash(reservationId, managementToken))
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }

    @Override
    @Transactional
    public Reservation cancel(UUID reservationId, String managementToken) {
        Reservation reservation = reservationPersistencePort.findByIdAndManagementTokenHashForUpdate(
                        reservationId, tokenHash(reservationId, managementToken))
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (reservation.status() == ReservationStatus.CANCELLED) {
            return reservation;
        }
        if (reservation.status() != ReservationStatus.PENDING
                && reservation.status() != ReservationStatus.CONFIRMED) {
            throw new InvalidReservationStateException("Reservation cannot be cancelled in its current state");
        }

        BookingSettings settings = bookingSettingsPort.findByRestaurantId(reservation.restaurantId())
                .orElseThrow(() -> new InvalidReservationStateException("Cancellation policy is not configured"));
        Integer cutoffMinutes = settings.customerCancellationCutoffMinutes();
        if (cutoffMinutes == null
                || Instant.now(clock).isAfter(reservation.startsAt().minus(Duration.ofMinutes(cutoffMinutes)))) {
            throw new InvalidReservationStateException("Reservation is past the customer cancellation cutoff");
        }
        if (reservation.bookingSlotId() == null
                || !bookingSlotPort.lockByIdAndRestaurantId(
                        reservation.bookingSlotId(), reservation.restaurantId())) {
            throw new InvalidReservationStateException("Reservation has no matching booking slot");
        }
        if (reservation.status() == ReservationStatus.CONFIRMED
                && !bookingSlotPort.releaseCapacity(reservation.bookingSlotId(), reservation.partySize())) {
            throw new InvalidReservationStateException("Reserved capacity could not be released");
        }

        Reservation cancelled = reservationPersistencePort.save(cancelled(reservation));
        eventPersistencePort.append(new ReservationEvent(
                UUID.randomUUID(),
                reservation.id(),
                ReservationEventType.CANCELLED_BY_CUSTOMER,
                null,
                "customer-cancel:" + reservation.id(),
                null,
                null,
                Instant.now(clock)));
        return cancelled;
    }

    private String tokenHash(UUID reservationId, String managementToken) {
        if (managementToken == null || managementToken.isBlank()) {
            throw new ReservationNotFoundException(reservationId);
        }
        return ReservationManagementToken.hash(managementToken);
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
}
