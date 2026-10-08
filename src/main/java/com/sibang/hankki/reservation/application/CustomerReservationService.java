package com.sibang.hankki.reservation.application;

import com.sibang.hankki.reservation.application.exception.InvalidReservationStateException;
import com.sibang.hankki.reservation.application.exception.ReservationNotFoundException;
import com.sibang.hankki.reservation.application.port.in.CustomerAccountReservationUseCase;
import com.sibang.hankki.reservation.application.port.in.CustomerAccountReservationUseCase.CustomerAccountReservation;
import com.sibang.hankki.reservation.application.port.in.CustomerReservationUseCase;
import com.sibang.hankki.reservation.application.port.out.ReservationEventPersistencePort;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort;
import com.sibang.hankki.reservation.domain.model.Reservation;
import com.sibang.hankki.reservation.domain.model.ReservationEvent;
import com.sibang.hankki.reservation.domain.model.ReservationEventType;
import com.sibang.hankki.reservation.domain.model.ReservationStatus;
import com.sibang.hankki.reservation.domain.model.VisitStatus;
import com.sibang.hankki.restaurant.application.port.out.BookingSettings;
import com.sibang.hankki.restaurant.application.port.out.BookingSettingsPort;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantData;
import com.sibang.hankki.user.application.exception.CustomerProfileNotFoundException;
import com.sibang.hankki.user.application.port.in.CustomerProfileUseCase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerReservationService implements CustomerReservationUseCase, CustomerAccountReservationUseCase {

    private final ReservationPersistencePort reservationPersistencePort;
    private final ReservationEventPersistencePort eventPersistencePort;
    private final BookingSettingsPort bookingSettingsPort;
    private final BookingSlotPort bookingSlotPort;
    private final RestaurantCatalogPort restaurantCatalogPort;
    private final CustomerProfileUseCase customerProfileUseCase;
    private final Clock clock;

    public CustomerReservationService(
            ReservationPersistencePort reservationPersistencePort,
            ReservationEventPersistencePort eventPersistencePort,
            BookingSettingsPort bookingSettingsPort,
            BookingSlotPort bookingSlotPort,
            RestaurantCatalogPort restaurantCatalogPort,
            CustomerProfileUseCase customerProfileUseCase,
            Clock clock) {
        this.reservationPersistencePort = reservationPersistencePort;
        this.eventPersistencePort = eventPersistencePort;
        this.bookingSettingsPort = bookingSettingsPort;
        this.bookingSlotPort = bookingSlotPort;
        this.restaurantCatalogPort = restaurantCatalogPort;
        this.customerProfileUseCase = customerProfileUseCase;
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
        return cancel(reservation, null);
    }

    @Override
    @Transactional
    public String issueCheckInToken(UUID reservationId, String managementToken) {
        Reservation reservation = reservationPersistencePort.findByIdAndManagementTokenHashForUpdate(
                        reservationId, tokenHash(reservationId, managementToken))
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        return issueCheckInToken(reservation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerAccountReservation> findAccountReservations(UUID customerId) {
        requireActiveCustomer(customerId);
        return reservationPersistencePort.findAllByCustomerId(customerId).stream()
                .map(this::accountReservation)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerAccountReservation findAccountReservation(UUID reservationId, UUID customerId) {
        requireActiveCustomer(customerId);
        return accountReservation(reservationPersistencePort.findByIdAndCustomerId(reservationId, customerId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId)));
    }

    @Override
    @Transactional
    public CustomerAccountReservation cancelAccountReservation(UUID reservationId, UUID customerId) {
        requireActiveCustomer(customerId);
        Reservation reservation = reservationPersistencePort.findByIdAndCustomerIdForUpdate(reservationId, customerId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        return accountReservation(cancel(reservation, customerId));
    }

    @Override
    @Transactional
    public String issueAccountCheckInToken(UUID reservationId, UUID customerId) {
        requireActiveCustomer(customerId);
        Reservation reservation = reservationPersistencePort.findByIdAndCustomerIdForUpdate(reservationId, customerId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        return issueCheckInToken(reservation);
    }

    private String issueCheckInToken(Reservation reservation) {
        if (reservation.status() != ReservationStatus.CONFIRMED
                || reservation.visitStatus() != VisitStatus.EXPECTED) {
            throw new InvalidReservationStateException(
                    "Check-in tokens require a confirmed reservation with EXPECTED visit status");
        }
        String checkInToken = ReservationToken.generate();
        reservationPersistencePort.save(withCheckInTokenHash(reservation, ReservationToken.hash(checkInToken)));
        return checkInToken;
    }

    private Reservation cancel(Reservation reservation, UUID actorUserId) {
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
                actorUserId,
                "customer-cancel:" + reservation.id(),
                null,
                null,
                Instant.now(clock)));
        return cancelled;
    }

    private void requireActiveCustomer(UUID customerId) {
        try {
            customerProfileUseCase.get(customerId);
        } catch (CustomerProfileNotFoundException exception) {
            throw new ReservationNotFoundException(customerId);
        }
    }

    private CustomerAccountReservation accountReservation(Reservation reservation) {
        RestaurantData restaurant = restaurantCatalogPort.findRestaurantById(reservation.restaurantId())
                .orElseThrow(() -> new ReservationNotFoundException(reservation.id()));
        return new CustomerAccountReservation(reservation, restaurant.slug(), restaurant.name());
    }

    private String tokenHash(UUID reservationId, String managementToken) {
        if (managementToken == null || managementToken.isBlank()) {
            throw new ReservationNotFoundException(reservationId);
        }
        return ReservationToken.hash(managementToken);
    }

    private Reservation withCheckInTokenHash(Reservation reservation, String checkInTokenHash) {
        return new Reservation(
                reservation.id(), reservation.reference(), reservation.idempotencyKey(), reservation.requestFingerprint(),
                reservation.restaurantId(), reservation.bookingSlotId(), reservation.customerId(), reservation.customerName(),
                reservation.customerEmail(), reservation.customerPhone(), reservation.startsAt(), reservation.endsAt(),
                reservation.partySize(), reservation.status(), reservation.capacityOverride(), reservation.visitStatus(),
                reservation.specialRequest(), reservation.preOrderNote(), reservation.managementTokenHash(),
                checkInTokenHash, reservation.checkedInAt(), reservation.checkedInBy(), reservation.version(),
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
}
