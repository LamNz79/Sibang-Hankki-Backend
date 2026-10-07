package com.sibang.hankki.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sibang.hankki.reservation.application.exception.InvalidReservationStateException;
import com.sibang.hankki.reservation.application.exception.ReservationNotFoundException;
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
import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;
import com.sibang.hankki.user.application.exception.CustomerProfileNotFoundException;
import com.sibang.hankki.user.application.port.in.CustomerProfileUseCase;
import com.sibang.hankki.user.application.port.in.CustomerProfileUseCase.CustomerProfile;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerReservationServiceTest {

    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SLOT_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID CUSTOMER_ID = UUID.fromString("70000000-0000-0000-0000-000000000002");
    private static final String TOKEN = "guest-management-token";
    private static final String TOKEN_HASH = ReservationManagementToken.hash(TOKEN);
    private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");

    @Mock
    private ReservationPersistencePort reservationPort;
    @Mock
    private ReservationEventPersistencePort eventPort;
    @Mock
    private BookingSettingsPort settingsPort;
    @Mock
    private BookingSlotPort slotPort;
    @Mock
    private RestaurantCatalogPort restaurantPort;
    @Mock
    private CustomerProfileUseCase customerProfileUseCase;

    private CustomerReservationService service;

    @BeforeEach
    void setUp() {
        service = new CustomerReservationService(
                reservationPort, eventPort, settingsPort, slotPort, restaurantPort, customerProfileUseCase,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void readsOnlyWithMatchingManagementToken() {
        Reservation confirmed = reservation(ReservationStatus.CONFIRMED, VisitStatus.EXPECTED);
        given(reservationPort.findByIdAndManagementTokenHash(RESERVATION_ID, TOKEN_HASH))
                .willReturn(Optional.of(confirmed));

        assertThat(service.findById(RESERVATION_ID, TOKEN)).isSameAs(confirmed);
        assertThatThrownBy(() -> service.findById(RESERVATION_ID, "wrong"))
                .isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(() -> service.findById(RESERVATION_ID, null))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void confirmedCancellationReleasesCapacityAndAppendsEvent() {
        Reservation confirmed = reservation(ReservationStatus.CONFIRMED, VisitStatus.EXPECTED);
        given(reservationPort.findByIdAndManagementTokenHashForUpdate(RESERVATION_ID, TOKEN_HASH))
                .willReturn(Optional.of(confirmed));
        given(settingsPort.findByRestaurantId(RESTAURANT_ID)).willReturn(Optional.of(settings(120)));
        given(slotPort.lockByIdAndRestaurantId(SLOT_ID, RESTAURANT_ID)).willReturn(true);
        given(slotPort.releaseCapacity(SLOT_ID, 2)).willReturn(true);
        given(reservationPort.save(org.mockito.ArgumentMatchers.any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        Reservation cancelled = service.cancel(RESERVATION_ID, TOKEN);

        assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancelled.visitStatus()).isNull();
        verify(slotPort).releaseCapacity(SLOT_ID, 2);
        ArgumentCaptor<ReservationEvent> event = ArgumentCaptor.forClass(ReservationEvent.class);
        verify(eventPort).append(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(ReservationEventType.CANCELLED_BY_CUSTOMER);
        assertThat(event.getValue().actorUserId()).isNull();
    }

    @Test
    void pendingCancellationDoesNotReleaseCapacity() {
        Reservation pending = reservation(ReservationStatus.PENDING, null);
        given(reservationPort.findByIdAndManagementTokenHashForUpdate(RESERVATION_ID, TOKEN_HASH))
                .willReturn(Optional.of(pending));
        given(settingsPort.findByRestaurantId(RESTAURANT_ID)).willReturn(Optional.of(settings(120)));
        given(slotPort.lockByIdAndRestaurantId(SLOT_ID, RESTAURANT_ID)).willReturn(true);
        given(reservationPort.save(org.mockito.ArgumentMatchers.any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.cancel(RESERVATION_ID, TOKEN).status()).isEqualTo(ReservationStatus.CANCELLED);
        verify(slotPort, never()).releaseCapacity(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void repeatedCancellationIsIdempotent() {
        Reservation cancelled = reservation(ReservationStatus.CANCELLED, null);
        given(reservationPort.findByIdAndManagementTokenHashForUpdate(RESERVATION_ID, TOKEN_HASH))
                .willReturn(Optional.of(cancelled));

        assertThat(service.cancel(RESERVATION_ID, TOKEN)).isSameAs(cancelled);
        verifyNoInteractions(settingsPort, slotPort, eventPort);
        verify(reservationPort, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cancellationAfterCutoffConflictsWithoutSideEffects() {
        Reservation confirmed = reservation(ReservationStatus.CONFIRMED, VisitStatus.EXPECTED, NOW.plusSeconds(60));
        given(reservationPort.findByIdAndManagementTokenHashForUpdate(RESERVATION_ID, TOKEN_HASH))
                .willReturn(Optional.of(confirmed));
        given(settingsPort.findByRestaurantId(RESTAURANT_ID)).willReturn(Optional.of(settings(120)));

        assertThatThrownBy(() -> service.cancel(RESERVATION_ID, TOKEN))
                .isInstanceOf(InvalidReservationStateException.class);
        verifyNoInteractions(slotPort, eventPort);
        verify(reservationPort, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void accountReadIsScopedToAnActiveCustomer() {
        givenActiveCustomer();
        Reservation confirmed = reservation(ReservationStatus.CONFIRMED, VisitStatus.EXPECTED);
        given(reservationPort.findByIdAndCustomerId(RESERVATION_ID, CUSTOMER_ID))
                .willReturn(Optional.of(confirmed));
        givenRestaurant();

        var result = service.findAccountReservation(RESERVATION_ID, CUSTOMER_ID);

        assertThat(result.reservation()).isSameAs(confirmed);
        assertThat(result.restaurantSlug()).isEqualTo("the-royal-pavilion");
        verify(reservationPort).findByIdAndCustomerId(RESERVATION_ID, CUSTOMER_ID);
    }

    @Test
    void staleCustomerSessionCannotReadAccountReservations() {
        given(customerProfileUseCase.get(CUSTOMER_ID)).willThrow(new CustomerProfileNotFoundException());
        assertThatThrownBy(() -> service.findAccountReservations(CUSTOMER_ID))
                .isInstanceOf(ReservationNotFoundException.class);
        verify(reservationPort, never()).findAllByCustomerId(CUSTOMER_ID);
    }

    @Test
    void accountCancellationReusesCapacityReleaseAndRecordsCustomerActor() {
        givenActiveCustomer();
        Reservation confirmed = reservation(ReservationStatus.CONFIRMED, VisitStatus.EXPECTED);
        given(reservationPort.findByIdAndCustomerIdForUpdate(RESERVATION_ID, CUSTOMER_ID))
                .willReturn(Optional.of(confirmed));
        given(settingsPort.findByRestaurantId(RESTAURANT_ID)).willReturn(Optional.of(settings(120)));
        given(slotPort.lockByIdAndRestaurantId(SLOT_ID, RESTAURANT_ID)).willReturn(true);
        given(slotPort.releaseCapacity(SLOT_ID, 2)).willReturn(true);
        given(reservationPort.save(org.mockito.ArgumentMatchers.any()))
                .willAnswer(invocation -> invocation.getArgument(0));
        givenRestaurant();

        var result = service.cancelAccountReservation(RESERVATION_ID, CUSTOMER_ID);

        assertThat(result.reservation().status()).isEqualTo(ReservationStatus.CANCELLED);
        verify(slotPort).releaseCapacity(SLOT_ID, 2);
        ArgumentCaptor<ReservationEvent> event = ArgumentCaptor.forClass(ReservationEvent.class);
        verify(eventPort).append(event.capture());
        assertThat(event.getValue().actorUserId()).isEqualTo(CUSTOMER_ID);
    }

    private void givenActiveCustomer() {
        given(customerProfileUseCase.get(CUSTOMER_ID)).willReturn(new CustomerProfile(
                CUSTOMER_ID, "customer", "Customer", "customer@example.com", null));
    }

    private void givenRestaurant() {
        given(restaurantPort.findRestaurantById(RESTAURANT_ID)).willReturn(Optional.of(new RestaurantData(
                RESTAURANT_ID, "the-royal-pavilion", "The Royal Pavilion", "", "", "", "", "", "", "", "")));
    }

    private BookingSettings settings(Integer cutoffMinutes) {
        return new BookingSettings(
                RESTAURANT_ID, 20, 60, 90, ConfirmationMode.AUTO, null,
                30, 1, 10, 11, cutoffMinutes);
    }

    private Reservation reservation(ReservationStatus status, VisitStatus visitStatus) {
        return reservation(status, visitStatus, Instant.parse("2026-10-10T11:30:00Z"));
    }

    private Reservation reservation(ReservationStatus status, VisitStatus visitStatus, Instant startsAt) {
        return new Reservation(
                RESERVATION_ID, "SHK-GUEST-1", "guest-key", "a".repeat(64), RESTAURANT_ID, SLOT_ID,
                null, "Minh Lam", null, "0900000000", startsAt, startsAt.plusSeconds(90 * 60L), 2,
                status, false, visitStatus, null, null, TOKEN_HASH, null, null, null, 0,
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"));
    }
}
