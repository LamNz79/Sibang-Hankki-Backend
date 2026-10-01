package com.sibang.hankki.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sibang.hankki.reservation.application.exception.InvalidReservationRequestException;
import com.sibang.hankki.reservation.application.exception.ReservationCapacityUnavailableException;
import com.sibang.hankki.reservation.application.exception.ReservationIdempotencyConflictException;
import com.sibang.hankki.reservation.application.exception.ReservationNotFoundException;
import com.sibang.hankki.reservation.application.exception.ReservationSlotNotFoundException;
import com.sibang.hankki.reservation.application.port.in.CreateReservationCommand;
import com.sibang.hankki.reservation.application.port.out.ReservationEventPersistencePort;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort;
import com.sibang.hankki.reservation.domain.model.Reservation;
import com.sibang.hankki.reservation.domain.model.ReservationEventType;
import com.sibang.hankki.reservation.domain.model.ReservationStatus;
import com.sibang.hankki.restaurant.application.port.out.BookingSettings;
import com.sibang.hankki.restaurant.application.port.out.BookingSettingsPort;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotData;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantData;
import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateReservationServiceTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SLOT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-10-01T03:00:00Z");

    @Mock
    private RestaurantCatalogPort restaurantCatalogPort;
    @Mock
    private BookingSettingsPort bookingSettingsPort;
    @Mock
    private BookingSlotPort bookingSlotPort;
    @Mock
    private ReservationPersistencePort reservationPersistencePort;
    @Mock
    private ReservationEventPersistencePort reservationEventPersistencePort;
    @Captor
    private ArgumentCaptor<Reservation> reservationCaptor;
    @Captor
    private ArgumentCaptor<com.sibang.hankki.reservation.domain.model.ReservationEvent> eventCaptor;

    private CreateReservationService service;

    @BeforeEach
    void setUp() {
        service = new CreateReservationService(
                restaurantCatalogPort,
                bookingSettingsPort,
                bookingSlotPort,
                reservationPersistencePort,
                reservationEventPersistencePort,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(restaurantCatalogPort.findActiveRestaurantBySlug("anan-saigon")).thenReturn(Optional.of(restaurant()));
        lenient().when(bookingSlotPort.findByRestaurantIdAndStartsAt(eq(RESTAURANT_ID), any()))
                .thenReturn(Optional.of(slot()));
        lenient().when(reservationPersistencePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(reservationEventPersistencePort.append(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void autoConfirmsAndReservesExactlyPartySize() {
        givenSettings(ConfirmationMode.AUTO, null);
        when(bookingSlotPort.reserveCapacity(SLOT_ID, 2)).thenReturn(true);

        var result = service.create(command("auto-key", 2));

        assertThat(result.reservation().status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(result.reservation().visitStatus().name()).isEqualTo("EXPECTED");
        assertThat(result.requiresRestaurantConfirmation()).isFalse();
        verify(bookingSlotPort).reserveCapacity(SLOT_ID, 2);
        verify(reservationEventPersistencePort, org.mockito.Mockito.times(2)).append(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).extracting(event -> event.eventType())
                .containsExactly(ReservationEventType.REQUESTED, ReservationEventType.CONFIRMED);
    }

    @Test
    void manualCreatesPendingWithoutReservingCapacity() {
        givenSettings(ConfirmationMode.MANUAL, null);

        var result = service.create(command("manual-key", 2));

        assertThat(result.reservation().status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(result.requiresRestaurantConfirmation()).isTrue();
        verify(bookingSlotPort, never()).reserveCapacity(any(), any(Integer.class));
        verify(reservationEventPersistencePort).append(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(ReservationEventType.REQUESTED);
    }

    @Test
    void hybridUsesAutomaticAndManualThresholds() {
        givenSettings(ConfirmationMode.HYBRID, 3);
        when(bookingSlotPort.reserveCapacity(SLOT_ID, 2)).thenReturn(true);

        assertThat(service.create(command("hybrid-auto", 2)).reservation().status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(service.create(command("hybrid-manual", 3)).reservation().status()).isEqualTo(ReservationStatus.PENDING);
        verify(bookingSlotPort).reserveCapacity(SLOT_ID, 2);
        verify(bookingSlotPort, never()).reserveCapacity(SLOT_ID, 3);
    }

    @Test
    void identicalIdempotencyReplayDoesNotReserveOrAppendAgain() {
        givenSettings(ConfirmationMode.AUTO, null);
        when(bookingSlotPort.reserveCapacity(SLOT_ID, 2)).thenReturn(true);
        var first = service.create(command("same-key", 2));
        when(reservationPersistencePort.findByIdempotencyKey("same-key"))
                .thenReturn(Optional.of(first.reservation()));

        var replay = service.create(command("same-key", 2));

        assertThat(replay.idempotentReplay()).isTrue();
        verify(bookingSlotPort).reserveCapacity(SLOT_ID, 2);
        verify(reservationEventPersistencePort, org.mockito.Mockito.times(2)).append(any());
    }

    @Test
    void differentRequestForExistingIdempotencyKeyConflicts() {
        givenSettings(ConfirmationMode.AUTO, null);
        when(bookingSlotPort.reserveCapacity(SLOT_ID, 2)).thenReturn(true);
        var first = service.create(command("same-key", 2));
        when(reservationPersistencePort.findByIdempotencyKey("same-key"))
                .thenReturn(Optional.of(first.reservation()));

        assertThatThrownBy(() -> service.create(command("same-key", 3)))
                .isInstanceOf(ReservationIdempotencyConflictException.class);
    }

    @Test
    void insufficientCapacityDoesNotPersistReservationOrEvents() {
        givenSettings(ConfirmationMode.AUTO, null);
        when(bookingSlotPort.reserveCapacity(SLOT_ID, 2)).thenReturn(false);

        assertThatThrownBy(() -> service.create(command("capacity-key", 2)))
                .isInstanceOf(ReservationCapacityUnavailableException.class);

        verify(reservationPersistencePort, never()).save(any());
        verify(reservationEventPersistencePort, never()).append(any());
    }

    @Test
    void rejectsPastDateAndUnknownRestaurantAndMissingSlot() {
        assertThatThrownBy(() -> service.create(command("past", 2, "2026-09-30")))
                .isInstanceOf(InvalidReservationRequestException.class);
        when(restaurantCatalogPort.findActiveRestaurantBySlug("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(command("missing", "missing", 2)))
                .isInstanceOf(ReservationNotFoundException.class);
        givenSettings(ConfirmationMode.AUTO, null);
        when(bookingSlotPort.findByRestaurantIdAndStartsAt(eq(RESTAURANT_ID), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(command("slot", 2)))
                .isInstanceOf(ReservationSlotNotFoundException.class);
    }

    @Test
    void rejectsEarlierTimeOnTheCurrentLocalDate() {
        givenSettings(ConfirmationMode.AUTO, null);

        assertThatThrownBy(() -> service.create(new CreateReservationCommand(
                "same-day-past", "anan-saigon", "2026-10-01", "09:30", 2,
                "Minh Lam", "0900000000", null, null, null)))
                .isInstanceOf(InvalidReservationRequestException.class);

        verify(bookingSlotPort, never()).findByRestaurantIdAndStartsAt(eq(RESTAURANT_ID), any());
    }

    private void givenSettings(ConfirmationMode mode, Integer manualThreshold) {
        when(bookingSettingsPort.findByRestaurantId(RESTAURANT_ID)).thenReturn(Optional.of(new BookingSettings(
                RESTAURANT_ID, 20, 60, 90, mode, manualThreshold, 30, 1, 10, 11)));
    }

    private CreateReservationCommand command(String key, int partySize) {
        return command(key, partySize, "2026-10-05");
    }

    private CreateReservationCommand command(String key, int partySize, String date) {
        return command(key, "anan-saigon", partySize, date);
    }

    private CreateReservationCommand command(String key, String slug, int partySize) {
        return command(key, slug, partySize, "2026-10-05");
    }

    private CreateReservationCommand command(String key, String slug, int partySize, String date) {
        return new CreateReservationCommand(
                key, slug, date, "18:30", partySize, "Minh Lam", "0900000000", null, null, null);
    }

    private RestaurantData restaurant() {
        return new RestaurantData(RESTAURANT_ID, "anan-saigon", "Anan", "", "", "", "", "", "", "", "");
    }

    private BookingSlotData slot() {
        Instant startsAt = LocalDate.of(2026, 10, 5).atTime(18, 30)
                .atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        return new BookingSlotData(SLOT_ID, RESTAURANT_ID, startsAt, startsAt.plusSeconds(90 * 60));
    }
}
