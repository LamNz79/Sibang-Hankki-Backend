package com.sibang.hankki.reservation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sibang.hankki.reservation.application.exception.InvalidReservationStateException;
import com.sibang.hankki.reservation.application.exception.ReservationNotFoundException;
import com.sibang.hankki.reservation.application.port.in.ReservationProposalUseCase.OwnerProposalCommand;
import com.sibang.hankki.reservation.application.port.out.ReservationEventPersistencePort;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort;
import com.sibang.hankki.reservation.application.port.out.ReservationProposalPersistencePort;
import com.sibang.hankki.reservation.domain.model.Reservation;
import com.sibang.hankki.reservation.domain.model.ReservationProposal;
import com.sibang.hankki.reservation.domain.model.ReservationProposalStatus;
import com.sibang.hankki.reservation.domain.model.ReservationStatus;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotData;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
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
class ReservationProposalServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T02:00:00Z");
    private static final UUID RESERVATION_ID = UUID.randomUUID();
    private static final UUID RESTAURANT_ID = UUID.randomUUID();
    private static final UUID SLOT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    @Mock private ReservationPersistencePort reservationPort;
    @Mock private ReservationProposalPersistencePort proposalPort;
    @Mock private ReservationEventPersistencePort eventPort;
    @Mock private BookingSlotPort bookingSlotPort;
    @Mock private RestaurantCatalogPort restaurantCatalogPort;
    @Mock private CustomerProfileUseCase customerProfileUseCase;

    private ReservationProposalService service;

    @BeforeEach
    void setUp() {
        service = new ReservationProposalService(
                reservationPort, proposalPort, eventPort, bookingSlotPort,
                restaurantCatalogPort, customerProfileUseCase,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void ownerCreatesProposalWithoutReservingCapacity() {
        Reservation pending = reservation(ReservationStatus.PENDING, 0);
        Instant proposedStart = Instant.parse("2026-10-14T11:00:00Z");
        when(restaurantCatalogPort.findTimezoneById(RESTAURANT_ID)).thenReturn(Optional.of("Asia/Ho_Chi_Minh"));
        when(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .thenReturn(Optional.of(pending));
        when(proposalPort.findPendingByReservationId(RESERVATION_ID)).thenReturn(Optional.empty());
        when(bookingSlotPort.findByRestaurantIdAndStartsAt(RESTAURANT_ID, proposedStart))
                .thenReturn(Optional.of(new BookingSlotData(
                        SLOT_ID, RESTAURANT_ID, proposedStart, proposedStart.plusSeconds(5400))));
        when(bookingSlotPort.hasAvailableCapacity(SLOT_ID, 2)).thenReturn(true);
        when(reservationPort.save(any())).thenAnswer(invocation -> withVersion(invocation.getArgument(0), 1));
        when(proposalPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(new OwnerProposalCommand(
                RESERVATION_ID, RESTAURANT_ID, ACTOR_ID, "2026-10-14", "18:00",
                "  Later table  ", "2026-10-14T10:00:00Z"));

        assertEquals(ReservationStatus.ALTERNATIVE_PROPOSED, result.reservation().status());
        assertEquals(1, result.proposal().reservationVersion());
        assertEquals("Later table", result.proposal().message());
        verify(bookingSlotPort, never()).reserveCapacity(any(), any(Integer.class));
    }

    @Test
    void guestAcceptsCurrentProposalAndReservesCapacity() {
        Reservation alternative = reservation(ReservationStatus.ALTERNATIVE_PROPOSED, 1);
        ReservationProposal proposal = proposal(1);
        when(reservationPort.findByIdAndManagementTokenHashForUpdate(any(), any()))
                .thenReturn(Optional.of(alternative));
        when(proposalPort.findByIdAndReservationIdForUpdate(proposal.id(), RESERVATION_ID))
                .thenReturn(Optional.of(proposal));
        when(bookingSlotPort.lockByIdAndRestaurantId(SLOT_ID, RESTAURANT_ID)).thenReturn(true);
        when(bookingSlotPort.reserveCapacity(SLOT_ID, 2)).thenReturn(true);
        when(bookingSlotPort.findByRestaurantIdAndStartsAt(RESTAURANT_ID, proposal.proposedStartsAt()))
                .thenReturn(Optional.of(new BookingSlotData(
                        SLOT_ID, RESTAURANT_ID, proposal.proposedStartsAt(),
                        proposal.proposedStartsAt().plusSeconds(5400))));
        when(reservationPort.save(any())).thenAnswer(invocation -> withVersion(invocation.getArgument(0), 2));
        when(proposalPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.acceptForGuest(RESERVATION_ID, proposal.id(), "management-token");

        assertEquals(ReservationStatus.CONFIRMED, result.reservation().status());
        assertEquals(ReservationProposalStatus.ACCEPTED, result.proposal().status());
        verify(bookingSlotPort).reserveCapacity(SLOT_ID, 2);
    }

    @Test
    void staleProposalIsRejectedBeforeCapacityChanges() {
        Reservation alternative = reservation(ReservationStatus.ALTERNATIVE_PROPOSED, 2);
        ReservationProposal stale = proposal(1);
        when(reservationPort.findByIdAndManagementTokenHashForUpdate(any(), any()))
                .thenReturn(Optional.of(alternative));
        when(proposalPort.findByIdAndReservationIdForUpdate(stale.id(), RESERVATION_ID))
                .thenReturn(Optional.of(stale));

        assertThrows(InvalidReservationStateException.class,
                () -> service.acceptForGuest(RESERVATION_ID, stale.id(), "management-token"));

        verify(bookingSlotPort, never()).reserveCapacity(any(), any(Integer.class));
        verify(reservationPort, never()).save(any());
    }

    @Test
    void declineDoesNotTouchSlotCapacity() {
        Reservation alternative = reservation(ReservationStatus.ALTERNATIVE_PROPOSED, 1);
        ReservationProposal proposal = proposal(1);
        when(reservationPort.findByIdAndManagementTokenHashForUpdate(any(), any()))
                .thenReturn(Optional.of(alternative));
        when(proposalPort.findByIdAndReservationIdForUpdate(proposal.id(), RESERVATION_ID))
                .thenReturn(Optional.of(proposal));
        when(reservationPort.save(any())).thenAnswer(invocation -> withVersion(invocation.getArgument(0), 2));
        when(proposalPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.declineForGuest(RESERVATION_ID, proposal.id(), "management-token");

        assertEquals(ReservationStatus.DECLINED, result.reservation().status());
        assertEquals(ReservationProposalStatus.DECLINED, result.proposal().status());
        verify(bookingSlotPort, never()).reserveCapacity(any(), any(Integer.class));
        verify(bookingSlotPort, never()).releaseCapacity(any(), any(Integer.class));
    }

    @Test
    void acceptRejectsProposedTimeAtOrBeforeCurrentTime() {
        Reservation alternative = reservation(ReservationStatus.ALTERNATIVE_PROPOSED, 1);
        ReservationProposal elapsed = proposal(1, NOW, null);
        stubGuestReservation(alternative, elapsed);

        assertThrows(InvalidReservationStateException.class,
                () -> service.acceptForGuest(RESERVATION_ID, elapsed.id(), "management-token"));

        verify(bookingSlotPort, never()).reserveCapacity(any(), any(Integer.class));
    }

    @Test
    void acceptRejectsExpiredRespondByDeadline() {
        Reservation alternative = reservation(ReservationStatus.ALTERNATIVE_PROPOSED, 1);
        ReservationProposal expired = proposal(
                1, Instant.parse("2026-10-14T11:00:00Z"), NOW);
        stubGuestReservation(alternative, expired);

        assertThrows(InvalidReservationStateException.class,
                () -> service.acceptForGuest(RESERVATION_ID, expired.id(), "management-token"));

        verify(bookingSlotPort, never()).reserveCapacity(any(), any(Integer.class));
    }

    @Test
    void ownerCannotProposeTheOriginallyRequestedSlot() {
        Reservation pending = reservation(ReservationStatus.PENDING, 0);
        when(restaurantCatalogPort.findTimezoneById(RESTAURANT_ID)).thenReturn(Optional.of("Asia/Ho_Chi_Minh"));
        when(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, RESTAURANT_ID))
                .thenReturn(Optional.of(pending));
        when(proposalPort.findPendingByReservationId(RESERVATION_ID)).thenReturn(Optional.empty());
        when(bookingSlotPort.findByRestaurantIdAndStartsAt(RESTAURANT_ID, pending.startsAt()))
                .thenReturn(Optional.of(new BookingSlotData(
                        pending.bookingSlotId(), RESTAURANT_ID, pending.startsAt(), pending.endsAt())));

        assertThrows(InvalidReservationStateException.class, () -> service.create(new OwnerProposalCommand(
                RESERVATION_ID, RESTAURANT_ID, ACTOR_ID, "2026-10-14", "17:00", null, null)));

        verify(bookingSlotPort, never()).hasAvailableCapacity(any(), any(Integer.class));
    }

    @Test
    void otherRestaurantOwnerGetsNotFoundWithoutCapacityAccess() {
        UUID otherRestaurantId = UUID.randomUUID();
        when(restaurantCatalogPort.findTimezoneById(otherRestaurantId)).thenReturn(Optional.of("Asia/Ho_Chi_Minh"));
        when(reservationPort.findByIdAndRestaurantIdForUpdate(RESERVATION_ID, otherRestaurantId))
                .thenReturn(Optional.empty());

        assertThrows(ReservationNotFoundException.class, () -> service.create(new OwnerProposalCommand(
                RESERVATION_ID, otherRestaurantId, ACTOR_ID, "2026-10-14", "18:00", null, null)));

        verify(bookingSlotPort, never()).reserveCapacity(any(), any(Integer.class));
    }

    @Test
    void otherAccountGetsNotFoundWithoutCapacityAccess() {
        UUID otherCustomerId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        when(customerProfileUseCase.get(otherCustomerId)).thenReturn(new CustomerProfile(
                otherCustomerId, "other-customer", "Other Customer", "other@example.com", null));
        when(reservationPort.findByIdAndCustomerIdForUpdate(RESERVATION_ID, otherCustomerId))
                .thenReturn(Optional.empty());

        assertThrows(ReservationNotFoundException.class,
                () -> service.acceptForAccount(RESERVATION_ID, proposalId, otherCustomerId));

        verify(bookingSlotPort, never()).reserveCapacity(any(), any(Integer.class));
    }

    @Test
    void invalidGuestTokenGetsNotFoundWithoutCapacityAccess() {
        UUID proposalId = UUID.randomUUID();
        when(reservationPort.findByIdAndManagementTokenHashForUpdate(any(), any()))
                .thenReturn(Optional.empty());

        assertThrows(ReservationNotFoundException.class,
                () -> service.acceptForGuest(RESERVATION_ID, proposalId, "wrong-token"));

        verify(bookingSlotPort, never()).reserveCapacity(any(), any(Integer.class));
    }

    private Reservation reservation(ReservationStatus status, int version) {
        return new Reservation(
                RESERVATION_ID, "SHK-TEST", "idem", "a".repeat(64), RESTAURANT_ID,
                UUID.randomUUID(), null, "Customer", null, "0900000000",
                Instant.parse("2026-10-14T10:00:00Z"), Instant.parse("2026-10-14T11:30:00Z"),
                2, status, false, null, null, null, ReservationToken.hash("management-token"),
                null, null, null, version, NOW.minusSeconds(3600), NOW.minusSeconds(3600));
    }

    private ReservationProposal proposal(int reservationVersion) {
        return proposal(reservationVersion, Instant.parse("2026-10-14T11:00:00Z"), null);
    }

    private ReservationProposal proposal(int reservationVersion, Instant startsAt, Instant respondBy) {
        return new ReservationProposal(
                UUID.randomUUID(), RESERVATION_ID, SLOT_ID, startsAt,
                2, reservationVersion, "Later", respondBy, ReservationProposalStatus.PENDING,
                ACTOR_ID, NOW, null, null);
    }

    private void stubGuestReservation(Reservation reservation, ReservationProposal proposal) {
        when(reservationPort.findByIdAndManagementTokenHashForUpdate(any(), any()))
                .thenReturn(Optional.of(reservation));
        when(proposalPort.findByIdAndReservationIdForUpdate(proposal.id(), RESERVATION_ID))
                .thenReturn(Optional.of(proposal));
    }

    private Reservation withVersion(Reservation reservation, int version) {
        return new Reservation(
                reservation.id(), reservation.reference(), reservation.idempotencyKey(),
                reservation.requestFingerprint(), reservation.restaurantId(), reservation.bookingSlotId(),
                reservation.customerId(), reservation.customerName(), reservation.customerEmail(),
                reservation.customerPhone(), reservation.startsAt(), reservation.endsAt(), reservation.partySize(),
                reservation.status(), reservation.capacityOverride(), reservation.visitStatus(),
                reservation.specialRequest(), reservation.preOrderNote(), reservation.managementTokenHash(),
                reservation.checkInTokenHash(), reservation.checkedInAt(), reservation.checkedInBy(), version,
                reservation.createdAt(), reservation.updatedAt());
    }
}
