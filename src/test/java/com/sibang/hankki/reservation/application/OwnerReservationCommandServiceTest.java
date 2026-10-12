package com.sibang.hankki.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sibang.hankki.reservation.application.exception.InvalidReservationRequestException;
import com.sibang.hankki.reservation.application.exception.InvalidReservationStateException;
import com.sibang.hankki.reservation.application.exception.NoActiveBookingSlotException;
import com.sibang.hankki.reservation.application.exception.ReservationCapacityUnavailableException;
import com.sibang.hankki.reservation.application.exception.ReservationIdempotencyConflictException;
import com.sibang.hankki.reservation.application.exception.ReservationNotFoundException;
import com.sibang.hankki.reservation.application.port.in.OwnerReservationCommandUseCase.OwnerWalkInCommand;
import com.sibang.hankki.reservation.application.port.out.ReservationEventPersistencePort;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort;
import com.sibang.hankki.reservation.domain.model.Reservation;
import com.sibang.hankki.reservation.domain.model.ReservationEvent;
import com.sibang.hankki.reservation.domain.model.ReservationEventType;
import com.sibang.hankki.reservation.domain.model.ReservationStatus;
import com.sibang.hankki.reservation.domain.model.VisitStatus;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotPort;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotData;
import com.sibang.hankki.restaurant.application.port.out.BookingSettings;
import com.sibang.hankki.restaurant.application.port.out.BookingSettingsPort;
import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
    private static final String CHECK_IN_TOKEN = "opaque-check-in-token";
    private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");

    @Mock
    private ReservationPersistencePort reservationPort;
    @Mock
    private ReservationEventPersistencePort eventPort;
    @Mock
    private BookingSlotPort slotPort;
    @Mock
    private BookingSettingsPort settingsPort;

    private OwnerReservationCommandService service;

    @BeforeEach
    void setUp() {
        service = new OwnerReservationCommandService(
                reservationPort,
                eventPort,
                slotPort,
                settingsPort,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsCapacitySafeWalkInAndReplaysWithoutDuplicates() {
        String idempotencyKey = "walk-in-1";
        OwnerWalkInCommand command = new OwnerWalkInCommand(
                idempotencyKey, RESTAURANT_ID, ACTOR_ID, "  Nguyen Van A  ", " 0901234567 ", 4, " Window ");
        BookingSlotData slot = new BookingSlotData(
                SLOT_ID, RESTAURANT_ID, NOW.minusSeconds(900), NOW.plusSeconds(2700));
        AtomicReference<Reservation> persisted = new AtomicReference<>();
        given(reservationPort.findByIdempotencyKey(idempotencyKey))
                .willAnswer(invocation -> Optional.ofNullable(persisted.get()));
        given(slotPort.findLatestActive(RESTAURANT_ID, NOW)).willReturn(Optional.of(slot));
        given(slotPort.reserveCapacity(SLOT_ID, 4)).willReturn(true);
        given(reservationPort.save(org.mockito.ArgumentMatchers.any())).willAnswer(invocation -> {
            Reservation reservation = invocation.getArgument(0);
            persisted.set(reservation);
            return reservation;
        });

        var created = service.createWalkIn(command);
        var replay = service.createWalkIn(command);

        assertThat(created.idempotentReplay()).isFalse();
        assertThat(replay.idempotentReplay()).isTrue();
        assertThat(replay.reservation().id()).isEqualTo(created.reservation().id());
        Reservation reservation = created.reservation();
        assertThat(reservation.restaurantId()).isEqualTo(RESTAURANT_ID);
        assertThat(reservation.bookingSlotId()).isEqualTo(SLOT_ID);
        assertThat(reservation.startsAt()).isEqualTo(slot.startsAt());
        assertThat(reservation.endsAt()).isEqualTo(slot.endsAt());
        assertThat(reservation.customerName()).isEqualTo("Nguyen Van A");
        assertThat(reservation.customerPhone()).isEqualTo("0901234567");
        assertThat(reservation.partySize()).isEqualTo(4);
        assertThat(reservation.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.visitStatus()).isEqualTo(VisitStatus.ARRIVED);
        assertThat(reservation.checkedInAt()).isEqualTo(NOW);
        assertThat(reservation.checkedInBy()).isEqualTo(ACTOR_ID);
        verify(slotPort).reserveCapacity(SLOT_ID, 4);
        verify(reservationPort).save(org.mockito.ArgumentMatchers.any());
        ArgumentCaptor<ReservationEvent> events = ArgumentCaptor.forClass(ReservationEvent.class);
        verify(eventPort, org.mockito.Mockito.times(3)).append(events.capture());
        assertThat(events.getAllValues()).extracting(ReservationEvent::eventType)
                .containsExactly(
                        ReservationEventType.REQUESTED,
                        ReservationEventType.CONFIRMED,
                        ReservationEventType.CHECKED_IN);
        assertThat(events.getAllValues()).extracting(ReservationEvent::actorUserId)
                .containsOnly(ACTOR_ID);
        assertThat(events.getAllValues()).extracting(ReservationEvent::createdAt)
                .containsExactly(NOW, NOW.plusNanos(1_000), NOW.plusNanos(2_000));
    }

    @Test
    void walkInRejectsMissingSlotAndInsufficientCapacityWithoutSaving() {
        OwnerWalkInCommand command = walkInCommand("walk-in-unavailable", RESTAURANT_ID);
        given(reservationPort.findByIdempotencyKey(command.idempotencyKey())).willReturn(Optional.empty());
        given(slotPort.findLatestActive(RESTAURANT_ID, NOW))
                .willReturn(Optional.empty(), Optional.of(new BookingSlotData(
                        SLOT_ID, RESTAURANT_ID, NOW.minusSeconds(60), NOW.plusSeconds(60))));
        given(slotPort.reserveCapacity(SLOT_ID, 4)).willReturn(false);

        assertThatThrownBy(() -> service.createWalkIn(command))
                .isInstanceOf(NoActiveBookingSlotException.class);
        assertThatThrownBy(() -> service.createWalkIn(command))
                .isInstanceOf(ReservationCapacityUnavailableException.class);

        verify(reservationPort, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(eventPort);
    }

    @Test
    void walkInIdempotencyNeverReturnsAnotherRestaurantsReservation() {
        OwnerWalkInCommand command = walkInCommand("shared-key", RESTAURANT_ID);
        Reservation otherRestaurantReservation = new Reservation(
                RESERVATION_ID, "SHK-OTHER", "shared-key", "a".repeat(64), UUID.randomUUID(), SLOT_ID,
                null, "Guest", null, "0901234567", NOW.minusSeconds(60), NOW.plusSeconds(60), 4,
                ReservationStatus.CONFIRMED, false, VisitStatus.ARRIVED, null, null, null, null,
                NOW, ACTOR_ID, 0, NOW, NOW);
        given(reservationPort.findByIdempotencyKey("shared-key"))
                .willReturn(Optional.of(otherRestaurantReservation));

        assertThatThrownBy(() -> service.createWalkIn(command))
                .isInstanceOf(ReservationIdempotencyConflictException.class);

        verifyNoInteractions(slotPort, eventPort);
        verify(reservationPort, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void walkInValidatesRequiredInputBeforePersistence() {
        assertThatThrownBy(() -> service.createWalkIn(new OwnerWalkInCommand(
                "key", RESTAURANT_ID, ACTOR_ID, " ", "0901234567", 4, null)))
                .isInstanceOf(InvalidReservationRequestException.class);
        assertThatThrownBy(() -> service.createWalkIn(new OwnerWalkInCommand(
                "key", RESTAURANT_ID, ACTOR_ID, "Guest", " ", 4, null)))
                .isInstanceOf(InvalidReservationRequestException.class);
        assertThatThrownBy(() -> service.createWalkIn(new OwnerWalkInCommand(
                "key", RESTAURANT_ID, ACTOR_ID, "Guest", "0901234567", 0, null)))
                .isInstanceOf(InvalidReservationRequestException.class);
        assertThatThrownBy(() -> service.createWalkIn(new OwnerWalkInCommand(
                " ", RESTAURANT_ID, ACTOR_ID, "Guest", "0901234567", 4, null)))
                .isInstanceOf(InvalidReservationRequestException.class);

        verifyNoInteractions(reservationPort, slotPort, eventPort);
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

    @Test
    void cancelsConfirmedExpectedReservationAndReleasesCapacity() {
        Reservation confirmed = reservation(ReservationStatus.CONFIRMED, VisitStatus.EXPECTED);
        given(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .willReturn(Optional.of(confirmed));
        given(slotPort.lockByIdAndRestaurantId(SLOT_ID, RESTAURANT_ID)).willReturn(true);
        given(slotPort.releaseCapacity(SLOT_ID, 2)).willReturn(true);
        given(reservationPort.save(org.mockito.ArgumentMatchers.any())).willAnswer(invocation -> invocation.getArgument(0));

        Reservation cancelled = service.cancel(
                RESERVATION_ID, RESTAURANT_ID, ACTOR_ID, "  Restaurant closed unexpectedly  ");

        assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancelled.visitStatus()).isNull();
        verify(slotPort).releaseCapacity(SLOT_ID, 2);
        ArgumentCaptor<ReservationEvent> event = ArgumentCaptor.forClass(ReservationEvent.class);
        verify(eventPort).append(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(ReservationEventType.CANCELLED_BY_RESTAURANT);
        assertThat(event.getValue().actorUserId()).isEqualTo(ACTOR_ID);
        assertThat(event.getValue().metadata())
                .isEqualTo("{\"reason\":\"Restaurant closed unexpectedly\"}");
    }

    @Test
    void repeatedOwnerCancellationDoesNotReleaseOrAppendAgain() {
        Reservation cancelled = reservation(ReservationStatus.CANCELLED, null);
        given(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .willReturn(Optional.of(cancelled));

        assertThat(service.cancel(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID, "Closed")).isSameAs(cancelled);

        verify(reservationPort, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(slotPort, eventPort);
    }

    @Test
    void ownerCancellationRejectsPendingAndPostArrivalStates() {
        given(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .willReturn(
                        Optional.of(reservation(ReservationStatus.PENDING, null)),
                        Optional.of(reservation(ReservationStatus.CONFIRMED, VisitStatus.ARRIVED)),
                        Optional.of(reservation(ReservationStatus.CONFIRMED, VisitStatus.SEATED)),
                        Optional.of(reservation(ReservationStatus.CONFIRMED, VisitStatus.COMPLETED)));

        for (int attempt = 0; attempt < 4; attempt++) {
            assertThatThrownBy(() -> service.cancel(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID, "Closed"))
                    .isInstanceOf(InvalidReservationStateException.class);
        }

        verify(reservationPort, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(slotPort, eventPort);
    }

    @Test
    void ownerCancellationRequiresReasonWithinLimit() {
        assertThatThrownBy(() -> service.cancel(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID, "   "))
                .isInstanceOf(InvalidReservationRequestException.class);
        assertThatThrownBy(() -> service.cancel(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID, "x".repeat(501)))
                .isInstanceOf(InvalidReservationRequestException.class);

        verifyNoInteractions(reservationPort, slotPort, eventPort);
    }

    @Test
    void ownerCancellationFromAnotherRestaurantIsNotFound() {
        given(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID, "Closed"))
                .isInstanceOf(ReservationNotFoundException.class);
        verifyNoInteractions(slotPort, eventPort);
    }

    @Test
    void checksInExpectedReservationAndRecordsActorAndTime() {
        Reservation confirmed = reservation(ReservationStatus.CONFIRMED, VisitStatus.EXPECTED);
        given(reservationPort.findByCheckInTokenHashAndRestaurantIdForUpdate(
                ReservationToken.hash(CHECK_IN_TOKEN), RESTAURANT_ID)).willReturn(Optional.of(confirmed));
        given(reservationPort.save(org.mockito.ArgumentMatchers.any())).willAnswer(invocation -> invocation.getArgument(0));

        Reservation arrived = service.checkIn(CHECK_IN_TOKEN, RESTAURANT_ID, ACTOR_ID);

        assertThat(arrived.visitStatus()).isEqualTo(VisitStatus.ARRIVED);
        assertThat(arrived.checkedInAt()).isEqualTo(NOW);
        assertThat(arrived.checkedInBy()).isEqualTo(ACTOR_ID);
        ArgumentCaptor<ReservationEvent> event = ArgumentCaptor.forClass(ReservationEvent.class);
        verify(eventPort).append(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(ReservationEventType.CHECKED_IN);
        assertThat(event.getValue().actorUserId()).isEqualTo(ACTOR_ID);
        assertThat(event.getValue().createdAt()).isEqualTo(NOW);
    }

    @Test
    void repeatedCheckInIsIdempotent() {
        Reservation arrived = reservation(
                ReservationStatus.CONFIRMED, VisitStatus.ARRIVED, NOW, ACTOR_ID);
        given(reservationPort.findByCheckInTokenHashAndRestaurantIdForUpdate(
                ReservationToken.hash(CHECK_IN_TOKEN), RESTAURANT_ID)).willReturn(Optional.of(arrived));

        assertThat(service.checkIn(CHECK_IN_TOKEN, RESTAURANT_ID, ACTOR_ID)).isSameAs(arrived);

        verify(reservationPort, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(eventPort, slotPort);
    }

    @Test
    void checksInByReservationIdUsingTheSameTransition() {
        Reservation confirmed = reservation(ReservationStatus.CONFIRMED, VisitStatus.EXPECTED);
        given(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .willReturn(Optional.of(confirmed));
        given(reservationPort.save(org.mockito.ArgumentMatchers.any())).willAnswer(invocation -> invocation.getArgument(0));

        Reservation arrived = service.checkIn(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID);

        assertThat(arrived.visitStatus()).isEqualTo(VisitStatus.ARRIVED);
        assertThat(arrived.checkedInAt()).isEqualTo(NOW);
        assertThat(arrived.checkedInBy()).isEqualTo(ACTOR_ID);
        ArgumentCaptor<ReservationEvent> event = ArgumentCaptor.forClass(ReservationEvent.class);
        verify(eventPort).append(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(ReservationEventType.CHECKED_IN);
    }

    @Test
    void manualCheckInRejectsInvalidStateAndOtherRestaurant() {
        Reservation pending = reservation(ReservationStatus.PENDING, null);
        given(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .willReturn(Optional.of(pending), Optional.empty());

        assertThatThrownBy(() -> service.checkIn(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID))
                .isInstanceOf(InvalidReservationStateException.class);
        assertThatThrownBy(() -> service.checkIn(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID))
                .isInstanceOf(ReservationNotFoundException.class);
        verify(reservationPort, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(eventPort, slotPort);
    }

    @Test
    void checkInTokenFromAnotherRestaurantIsNotFound() {
        given(reservationPort.findByCheckInTokenHashAndRestaurantIdForUpdate(
                ReservationToken.hash(CHECK_IN_TOKEN), RESTAURANT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkIn(CHECK_IN_TOKEN, RESTAURANT_ID, ACTOR_ID))
                .isInstanceOf(ReservationNotFoundException.class);
        verify(reservationPort, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(eventPort, slotPort);
    }

    @Test
    void seatsArrivedReservationAndRecordsEvent() {
        Reservation arrived = reservation(ReservationStatus.CONFIRMED, VisitStatus.ARRIVED);
        given(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .willReturn(Optional.of(arrived));
        given(reservationPort.save(org.mockito.ArgumentMatchers.any())).willAnswer(invocation -> invocation.getArgument(0));

        Reservation seated = service.seat(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID);

        assertThat(seated.visitStatus()).isEqualTo(VisitStatus.SEATED);
        ArgumentCaptor<ReservationEvent> event = ArgumentCaptor.forClass(ReservationEvent.class);
        verify(eventPort).append(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(ReservationEventType.SEATED);
        assertThat(event.getValue().actorUserId()).isEqualTo(ACTOR_ID);
    }

    @Test
    void completesSeatedReservationAndRecordsEvent() {
        Reservation seated = reservation(ReservationStatus.CONFIRMED, VisitStatus.SEATED);
        given(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .willReturn(Optional.of(seated));
        given(reservationPort.save(org.mockito.ArgumentMatchers.any())).willAnswer(invocation -> invocation.getArgument(0));

        Reservation completed = service.complete(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID);

        assertThat(completed.visitStatus()).isEqualTo(VisitStatus.COMPLETED);
        ArgumentCaptor<ReservationEvent> event = ArgumentCaptor.forClass(ReservationEvent.class);
        verify(eventPort).append(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(ReservationEventType.COMPLETED);
        assertThat(event.getValue().actorUserId()).isEqualTo(ACTOR_ID);
    }

    @Test
    void repeatedVisitTransitionsAreIdempotent() {
        Reservation seated = reservation(ReservationStatus.CONFIRMED, VisitStatus.SEATED);
        Reservation completed = reservation(ReservationStatus.CONFIRMED, VisitStatus.COMPLETED);
        given(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .willReturn(Optional.of(seated), Optional.of(completed));

        assertThat(service.seat(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID)).isSameAs(seated);
        assertThat(service.complete(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID)).isSameAs(completed);

        verify(reservationPort, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(eventPort, slotPort);
    }

    @Test
    void visitTransitionsCannotSkipOrMoveBackward() {
        Reservation expected = reservation(ReservationStatus.CONFIRMED, VisitStatus.EXPECTED);
        Reservation completed = reservation(ReservationStatus.CONFIRMED, VisitStatus.COMPLETED);
        Reservation arrived = reservation(ReservationStatus.CONFIRMED, VisitStatus.ARRIVED);
        given(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .willReturn(Optional.of(expected), Optional.of(completed), Optional.of(arrived));

        assertThatThrownBy(() -> service.seat(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID))
                .isInstanceOf(InvalidReservationStateException.class);
        assertThatThrownBy(() -> service.seat(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID))
                .isInstanceOf(InvalidReservationStateException.class);
        assertThatThrownBy(() -> service.complete(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID))
                .isInstanceOf(InvalidReservationStateException.class);

        verify(reservationPort, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(eventPort, slotPort);
    }

    @Test
    void visitTransitionFromAnotherRestaurantIsNotFound() {
        given(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.seat(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID))
                .isInstanceOf(ReservationNotFoundException.class);
        verify(reservationPort, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(eventPort, slotPort);
    }

    @Test
    void marksEligibleReservationNoShowAndRecordsActorWithoutTouchingCapacity() {
        Reservation expected = reservationAt(
                ReservationStatus.CONFIRMED, VisitStatus.EXPECTED, NOW.minusSeconds(15 * 60L));
        given(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .willReturn(Optional.of(expected));
        given(settingsPort.findByRestaurantId(RESTAURANT_ID)).willReturn(Optional.of(settings(15)));
        given(reservationPort.save(org.mockito.ArgumentMatchers.any())).willAnswer(invocation -> invocation.getArgument(0));

        Reservation result = service.markNoShow(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID);

        assertThat(result.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(result.visitStatus()).isEqualTo(VisitStatus.NO_SHOW);
        assertThat(result.checkedInAt()).isEqualTo(expected.checkedInAt());
        assertThat(result.checkedInBy()).isEqualTo(expected.checkedInBy());
        ArgumentCaptor<ReservationEvent> event = ArgumentCaptor.forClass(ReservationEvent.class);
        verify(eventPort).append(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(ReservationEventType.NO_SHOW);
        assertThat(event.getValue().actorUserId()).isEqualTo(ACTOR_ID);
        verifyNoInteractions(slotPort);
    }

    @Test
    void noShowRejectsBeforeGraceAndWhenGraceIsNotConfigured() {
        Reservation expected = reservationAt(
                ReservationStatus.CONFIRMED, VisitStatus.EXPECTED, NOW.minusSeconds(14 * 60L));
        given(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .willReturn(Optional.of(expected), Optional.of(expected));
        given(settingsPort.findByRestaurantId(RESTAURANT_ID))
                .willReturn(Optional.of(settings(15)), Optional.of(settings(null)));

        assertThatThrownBy(() -> service.markNoShow(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID))
                .isInstanceOf(InvalidReservationStateException.class)
                .hasMessageContaining("not yet eligible");
        assertThatThrownBy(() -> service.markNoShow(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID))
                .isInstanceOf(InvalidReservationStateException.class)
                .hasMessageContaining("not configured");

        verify(reservationPort, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(slotPort, eventPort);
    }

    @Test
    void noShowRejectsInvalidStateAndAnotherRestaurant() {
        Reservation pending = reservationAt(ReservationStatus.PENDING, null, NOW.minusSeconds(3600));
        given(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .willReturn(Optional.of(pending), Optional.empty());

        assertThatThrownBy(() -> service.markNoShow(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID))
                .isInstanceOf(InvalidReservationStateException.class);
        assertThatThrownBy(() -> service.markNoShow(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID))
                .isInstanceOf(ReservationNotFoundException.class);

        verifyNoInteractions(settingsPort, slotPort, eventPort);
        verify(reservationPort, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void repeatedNoShowIsIdempotent() {
        Reservation noShow = reservationAt(
                ReservationStatus.CONFIRMED, VisitStatus.NO_SHOW, NOW.minusSeconds(3600));
        given(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .willReturn(Optional.of(noShow));

        assertThat(service.markNoShow(RESERVATION_ID, RESTAURANT_ID, ACTOR_ID)).isSameAs(noShow);

        verifyNoInteractions(settingsPort, slotPort, eventPort);
        verify(reservationPort, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private Reservation reservation(ReservationStatus status, VisitStatus visitStatus) {
        return reservation(status, visitStatus, null, null);
    }

    private OwnerWalkInCommand walkInCommand(String idempotencyKey, UUID restaurantId) {
        return new OwnerWalkInCommand(
                idempotencyKey, restaurantId, ACTOR_ID, "Guest", "0901234567", 4, null);
    }

    private Reservation reservation(
            ReservationStatus status, VisitStatus visitStatus, Instant checkedInAt, UUID checkedInBy) {
        return reservationAt(status, visitStatus, Instant.parse("2026-10-10T11:30:00Z"), checkedInAt, checkedInBy);
    }

    private Reservation reservationAt(ReservationStatus status, VisitStatus visitStatus, Instant startsAt) {
        return reservationAt(status, visitStatus, startsAt, null, null);
    }

    private Reservation reservationAt(
            ReservationStatus status,
            VisitStatus visitStatus,
            Instant startsAt,
            Instant checkedInAt,
            UUID checkedInBy) {
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
                startsAt,
                startsAt.plusSeconds(90 * 60L),
                2,
                status,
                false,
                visitStatus,
                null,
                null,
                null,
                ReservationToken.hash(CHECK_IN_TOKEN),
                checkedInAt,
                checkedInBy,
                0,
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-01T00:00:00Z"));
    }

    private BookingSettings settings(Integer noShowGraceMinutes) {
        return new BookingSettings(
                RESTAURANT_ID, 20, 60, 90, ConfirmationMode.HYBRID, 7,
                30, 1, 10, 11, 120, noShowGraceMinutes);
    }
}
