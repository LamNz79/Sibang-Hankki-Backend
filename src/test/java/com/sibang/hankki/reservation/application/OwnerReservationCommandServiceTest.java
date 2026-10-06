package com.sibang.hankki.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sibang.hankki.reservation.application.exception.InvalidReservationStateException;
import com.sibang.hankki.reservation.application.exception.ReservationCapacityUnavailableException;
import com.sibang.hankki.reservation.application.port.out.ReservationEventPersistencePort;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort;
import com.sibang.hankki.reservation.domain.model.Reservation;
import com.sibang.hankki.reservation.domain.model.ReservationEvent;
import com.sibang.hankki.reservation.domain.model.ReservationStatus;
import com.sibang.hankki.reservation.domain.model.VisitStatus;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotPort;
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
class OwnerReservationCommandServiceTest {

    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SLOT_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");

    @Mock
    private ReservationPersistencePort reservationPort;
    @Mock
    private ReservationEventPersistencePort eventPort;
    @Mock
    private BookingSlotPort slotPort;

    private OwnerReservationCommandService service;

    @BeforeEach
    void setUp() {
        service = new OwnerReservationCommandService(
                reservationPort,
                eventPort,
                slotPort,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void confirmsPendingReservationAndRecordsActor() {
        Reservation pending = reservation(ReservationStatus.PENDING, null);
        given(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .willReturn(Optional.of(pending));
        given(slotPort.lockByIdAndRestaurantId(SLOT_ID, RESTAURANT_ID)).willReturn(true);
        given(slotPort.reserveCapacity(SLOT_ID, 2)).willReturn(true);
        given(reservationPort.save(org.mockito.ArgumentMatchers.any())).willAnswer(invocation -> invocation.getArgument(0));

        Reservation result = service.confirm(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID);

        assertThat(result.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(result.visitStatus()).isEqualTo(VisitStatus.EXPECTED);
        ArgumentCaptor<ReservationEvent> event = ArgumentCaptor.forClass(ReservationEvent.class);
        verify(eventPort).append(event.capture());
        assertThat(event.getValue().actorUserId()).isEqualTo(ACTOR_ID);
        assertThat(event.getValue().eventType().name()).isEqualTo("CONFIRMED");
    }

    @Test
    void repeatedConfirmDoesNotReserveOrAppendAgain() {
        Reservation confirmed = reservation(ReservationStatus.CONFIRMED, VisitStatus.EXPECTED);
        given(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .willReturn(Optional.of(confirmed));

        assertThat(service.confirm(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID)).isSameAs(confirmed);

        verifyNoInteractions(slotPort, eventPort);
        verify(reservationPort, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void insufficientCapacityLeavesReservationPending() {
        Reservation pending = reservation(ReservationStatus.PENDING, null);
        given(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .willReturn(Optional.of(pending));
        given(slotPort.lockByIdAndRestaurantId(SLOT_ID, RESTAURANT_ID)).willReturn(true);
        given(slotPort.reserveCapacity(SLOT_ID, 2)).willReturn(false);

        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID))
                .isInstanceOf(ReservationCapacityUnavailableException.class);
        verify(reservationPort, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(eventPort);
    }

    @Test
    void declinesWithoutCapacityChangeAndStoresReason() {
        Reservation pending = reservation(ReservationStatus.PENDING, null);
        given(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .willReturn(Optional.of(pending));
        given(reservationPort.save(org.mockito.ArgumentMatchers.any())).willAnswer(invocation -> invocation.getArgument(0));

        Reservation result = service.decline(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID, "Closed early");

        assertThat(result.status()).isEqualTo(ReservationStatus.DECLINED);
        assertThat(result.visitStatus()).isNull();
        verifyNoInteractions(slotPort);
        ArgumentCaptor<ReservationEvent> event = ArgumentCaptor.forClass(ReservationEvent.class);
        verify(eventPort).append(event.capture());
        assertThat(event.getValue().actorUserId()).isEqualTo(ACTOR_ID);
        assertThat(event.getValue().metadata()).isEqualTo("{\"reason\":\"Closed early\"}");
    }

    @Test
    void repeatedDeclineIsSafeAndOtherStatesConflict() {
        Reservation declined = reservation(ReservationStatus.DECLINED, null);
        given(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .willReturn(Optional.of(declined));
        assertThat(service.decline(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID, null)).isSameAs(declined);
        verify(reservationPort, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(slotPort, eventPort);

        Reservation cancelled = reservation(ReservationStatus.CANCELLED, null);
        given(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .willReturn(Optional.of(cancelled));
        assertThatThrownBy(() -> service.confirm(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID))
                .isInstanceOf(InvalidReservationStateException.class);
    }

    private Reservation reservation(ReservationStatus status, VisitStatus visitStatus) {
        return new Reservation(
                RESERVATION_ID,
                "SHK-COMMAND-1",
                "command-test-key",
                "a".repeat(64),
                RESTAURANT_ID,
                SLOT_ID,
                null,
                "Minh Lam",
                null,
                "0900000000",
                Instant.parse("2026-10-10T11:30:00Z"),
                Instant.parse("2026-10-10T13:00:00Z"),
                2,
                status,
                false,
                visitStatus,
                null,
                null,
                null,
                null,
                null,
                0,
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-01T00:00:00Z"));
    }
}
