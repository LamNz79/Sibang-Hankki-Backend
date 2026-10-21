package com.sibang.hankki.reservation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sibang.hankki.reservation.application.exception.InvalidReservationStateException;
import com.sibang.hankki.reservation.application.exception.ReservationCapacityUnavailableException;
import com.sibang.hankki.reservation.application.port.in.ReservationProposalUseCase.OwnerProposalCommand;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotPort;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(ReservationProposalPersistenceTest.FixedClockConfiguration.class)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class ReservationProposalPersistenceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T02:00:00Z");
    private static final Instant ORIGINAL_START = Instant.parse("2026-10-14T10:00:00Z");
    private static final Instant PROPOSED_START = Instant.parse("2026-10-14T11:00:00Z");
    private static final Instant REQUESTED_START = Instant.parse("2026-10-15T11:30:00Z");

    @Autowired private ReservationProposalService service;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private BookingSlotPort bookingSlotPort;

    private UUID restaurantId;
    private UUID actorId;
    private UUID reservationId;
    private UUID originalSlotId;
    private UUID proposedSlotId;
    private UUID requestedSlotId;

    @BeforeEach
    void setUp() {
        restaurantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        reservationId = UUID.randomUUID();
        originalSlotId = UUID.randomUUID();
        proposedSlotId = UUID.randomUUID();
        requestedSlotId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into restaurants (id, slug, name, city_slug, timezone, approval_status)
                values (?, ?, 'Proposal Test', 'ho-chi-minh-city', 'Asia/Ho_Chi_Minh', 'ACTIVE')
                """, restaurantId, "proposal-test-" + restaurantId);
        jdbcTemplate.update("""
                insert into users (id, userid, password_hash, name, role, status, restaurant_id)
                values (?, ?, 'unused-test-hash', 'Test Owner', 'OWNER', 'ACTIVE', ?)
                """, actorId, "proposal-" + actorId.toString().substring(0, 20), restaurantId);
        insertSlot(originalSlotId, ORIGINAL_START, 4, 0);
        insertSlot(proposedSlotId, PROPOSED_START, 4, 0);
        insertSlot(requestedSlotId, REQUESTED_START, 4, 0);
        jdbcTemplate.update("""
                insert into reservations (
                    id, reference, idempotency_key, request_fingerprint, restaurant_id, booking_slot_id,
                    customer_name, customer_phone, starts_at, ends_at, party_size, status,
                    management_token_hash)
                values (?, ?, ?, ?, ?, ?, 'Guest', '0900000000', ?, ?, 2, 'PENDING', ?)
                """, reservationId, "SHK-" + reservationId.toString().substring(0, 8),
                "proposal-" + reservationId, "0".repeat(64), restaurantId, originalSlotId,
                Timestamp.from(ORIGINAL_START), Timestamp.from(ORIGINAL_START.plusSeconds(5400)),
                ReservationToken.hash("management-token"));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from reservation_events where reservation_id = ?", reservationId);
        jdbcTemplate.update("delete from reservation_proposals where reservation_id = ?", reservationId);
        jdbcTemplate.update("delete from reservations where id = ?", reservationId);
        jdbcTemplate.update("delete from booking_slots where restaurant_id = ?", restaurantId);
        jdbcTemplate.update("delete from users where id = ?", actorId);
        jdbcTemplate.update("delete from restaurants where id = ?", restaurantId);
    }

    @Test
    void createDoesNotReserveAndGuestAcceptsAtomically() {
        var proposal = createProposal();
        assertEquals(0, capacityReserved());

        var accepted = service.acceptForGuest(
                reservationId, proposal.proposal().id(), "management-token");

        assertEquals("CONFIRMED", accepted.reservation().status().name());
        assertEquals("EXPECTED", accepted.reservation().visitStatus().name());
        assertEquals(proposedSlotId, accepted.reservation().bookingSlotId());
        assertEquals(2, capacityReserved());
        assertEquals("ACCEPTED", jdbcTemplate.queryForObject(
                "select status from reservation_proposals where id = ?", String.class, proposal.proposal().id()));
        assertEquals(1, eventCount("ALTERNATIVE_ACCEPTED"));
    }

    @Test
    void fullSlotLeavesProposalAndReservationUnchanged() {
        var proposal = createProposal();
        jdbcTemplate.update("update booking_slots set capacity_reserved = capacity_total where id = ?", proposedSlotId);

        assertThrows(ReservationCapacityUnavailableException.class,
                () -> service.acceptForGuest(reservationId, proposal.proposal().id(), "management-token"));

        assertEquals("ALTERNATIVE_PROPOSED", reservationStatus());
        assertEquals("PENDING", proposalStatus(proposal.proposal().id()));
        assertEquals(4, capacityReserved());
        assertEquals(0, eventCount("ALTERNATIVE_ACCEPTED"));
    }

    @Test
    void staleVersionAndDeclineDoNotTouchCapacity() {
        var proposal = createProposal();
        jdbcTemplate.update("update reservations set version = version + 1 where id = ?", reservationId);
        assertThrows(InvalidReservationStateException.class,
                () -> service.acceptForGuest(reservationId, proposal.proposal().id(), "management-token"));
        assertEquals(0, capacityReserved());

        jdbcTemplate.update("update reservations set version = version - 1 where id = ?", reservationId);
        var declined = service.declineForGuest(
                reservationId, proposal.proposal().id(), "management-token");
        assertEquals("DECLINED", declined.reservation().status().name());
        assertEquals("DECLINED", declined.proposal().status().name());
        assertEquals(0, capacityReserved());
        assertEquals(1, eventCount("ALTERNATIVE_DECLINED"));
    }

    @Test
    void guestRequestsAnotherTimeByUpdatingTheSameReservationOnce() {
        var proposal = createProposal();

        var updated = service.requestAnotherTimeForGuest(
                reservationId, proposal.proposal().id(), "management-token", "2026-10-15", "18:30");

        assertEquals(reservationId, updated.id());
        assertEquals("PENDING", updated.status().name());
        assertEquals(requestedSlotId, updated.bookingSlotId());
        assertEquals(REQUESTED_START, updated.startsAt());
        assertEquals(0, capacityReserved(proposedSlotId));
        assertEquals(0, capacityReserved(requestedSlotId));
        assertEquals("DECLINED", proposalStatus(proposal.proposal().id()));
        assertEquals(1, eventCount("ANOTHER_TIME_REQUESTED"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from reservations where id = ?", Integer.class, reservationId));

        var replay = service.requestAnotherTimeForGuest(
                reservationId, proposal.proposal().id(), "management-token", "2026-10-15", "18:30");
        assertEquals(reservationId, replay.id());
        assertEquals(1, eventCount("ANOTHER_TIME_REQUESTED"));
    }

    @Test
    void pendingChangeRequestUpdatesTheSameReservationWithoutProposalOrCapacity() {
        var updated = service.requestChangeForGuest(
                reservationId, "management-token", "2026-10-15", "18:30", "New request");

        var replay = service.requestChangeForGuest(
                reservationId, "management-token", "2026-10-15", "18:30", "New request");

        assertEquals(reservationId, updated.reservation().id());
        assertEquals("PENDING", updated.reservation().status().name());
        assertEquals(requestedSlotId, updated.reservation().bookingSlotId());
        assertEquals(REQUESTED_START, updated.reservation().startsAt());
        assertEquals(null, updated.proposal());
        assertEquals(0, capacityReserved(originalSlotId));
        assertEquals(0, capacityReserved(requestedSlotId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from reservation_proposals where reservation_id = ?",
                Integer.class, reservationId));
        assertEquals(1, eventCount("RESCHEDULED"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from reservations where id = ?", Integer.class, reservationId));
        assertEquals(reservationId, replay.reservation().id());
        assertEquals(requestedSlotId, replay.reservation().bookingSlotId());
        assertEquals(REQUESTED_START, replay.reservation().startsAt());
        assertEquals(1, eventCount("RESCHEDULED"));
        assertEquals(0, capacityReserved(originalSlotId));
        assertEquals(0, capacityReserved(requestedSlotId));
    }

    @Test
    void guestChangeRequestKeepsConfirmedBookingUntilOwnerAcceptsAndThenSwapsCapacity() {
        confirmOriginalReservation(2);

        var requested = service.requestChangeForGuest(
                reservationId, "management-token", "2026-10-15", "18:30", "Later please");

        assertEquals(reservationId, requested.reservation().id());
        assertEquals(ORIGINAL_START, requested.reservation().startsAt());
        assertEquals("CONFIRMED", reservationStatus());
        assertEquals(2, capacityReserved(originalSlotId));
        assertEquals(0, capacityReserved(requestedSlotId));
        assertEquals("CUSTOMER", jdbcTemplate.queryForObject(
                "select initiator from reservation_proposals where id = ?",
                String.class, requested.proposal().id()));
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from reservation_proposals where id = ? and created_by is not null",
                Integer.class, requested.proposal().id()));

        var accepted = service.acceptChangeRequest(
                reservationId, requested.proposal().id(), restaurantId, actorId);

        assertEquals(reservationId, accepted.reservation().id());
        assertEquals(requestedSlotId, accepted.reservation().bookingSlotId());
        assertEquals(REQUESTED_START, accepted.reservation().startsAt());
        assertEquals("CONFIRMED", accepted.reservation().status().name());
        assertEquals("EXPECTED", accepted.reservation().visitStatus().name());
        assertEquals(0, capacityReserved(originalSlotId));
        assertEquals(2, capacityReserved(requestedSlotId));
        assertEquals("ACCEPTED", proposalStatus(requested.proposal().id()));
        assertEquals(1, eventCount("RESCHEDULED"));

        service.acceptChangeRequest(reservationId, requested.proposal().id(), restaurantId, actorId);
        assertEquals(0, capacityReserved(originalSlotId));
        assertEquals(2, capacityReserved(requestedSlotId));
        assertEquals(1, eventCount("RESCHEDULED"));
    }

    @Test
    void ownerDeclineKeepsConfirmedBookingAndCapacityUnchanged() {
        confirmOriginalReservation(2);
        var requested = service.requestChangeForGuest(
                reservationId, "management-token", "2026-10-15", "18:30", null);

        var declined = service.declineChangeRequest(
                reservationId, requested.proposal().id(), restaurantId, actorId);

        assertEquals(originalSlotId, declined.reservation().bookingSlotId());
        assertEquals(ORIGINAL_START, declined.reservation().startsAt());
        assertEquals(2, capacityReserved(originalSlotId));
        assertEquals(0, capacityReserved(requestedSlotId));
        assertEquals("DECLINED", proposalStatus(requested.proposal().id()));
        assertEquals(1, eventCount("RESCHEDULE_DECLINED"));
    }

    @Test
    void failedOldCapacityReleaseRollsBackNewCapacityAndRequestState() {
        confirmOriginalReservation(0);
        var requested = service.requestChangeForGuest(
                reservationId, "management-token", "2026-10-15", "18:30", null);

        assertThrows(InvalidReservationStateException.class, () -> service.acceptChangeRequest(
                reservationId, requested.proposal().id(), restaurantId, actorId));

        assertEquals(originalSlotId, jdbcTemplate.queryForObject(
                "select booking_slot_id from reservations where id = ?", UUID.class, reservationId));
        assertEquals(0, capacityReserved(originalSlotId));
        assertEquals(0, capacityReserved(requestedSlotId));
        assertEquals("PENDING", proposalStatus(requested.proposal().id()));
        assertEquals(0, eventCount("RESCHEDULED"));
    }

    @Test
    @Transactional
    void slotRegenerationKeepsSlotsReferencedByReservationsAndProposals() {
        createProposal();
        bookingSlotPort.setBookable(List.of(originalSlotId, proposedSlotId, requestedSlotId), false);

        int deleted = bookingSlotPort.deleteUnreferencedSlots(
                restaurantId, ORIGINAL_START.minusSeconds(1), REQUESTED_START.plusSeconds(1));

        assertEquals(1, deleted);
        assertEquals(2, jdbcTemplate.queryForObject(
                "select count(*) from booking_slots where restaurant_id = ?", Integer.class, restaurantId));
    }

    private com.sibang.hankki.reservation.application.port.in.ReservationProposalUseCase.ProposalResult createProposal() {
        return service.create(new OwnerProposalCommand(
                reservationId, restaurantId, actorId, "2026-10-14", "18:00",
                "  We can prepare a table later.  ", "2026-10-14T10:00:00Z"));
    }

    private void insertSlot(UUID id, Instant startsAt, int total, int reserved) {
        jdbcTemplate.update("""
                insert into booking_slots
                    (id, restaurant_id, starts_at, ends_at, capacity_total, capacity_reserved)
                values (?, ?, ?, ?, ?, ?)
                """, id, restaurantId, Timestamp.from(startsAt), Timestamp.from(startsAt.plusSeconds(5400)),
                total, reserved);
    }

    private void confirmOriginalReservation(int reservedCapacity) {
        jdbcTemplate.update("""
                update reservations set status = 'CONFIRMED', visit_status = 'EXPECTED'
                where id = ?
                """, reservationId);
        jdbcTemplate.update(
                "update booking_slots set capacity_reserved = ? where id = ?",
                reservedCapacity, originalSlotId);
    }

    private int capacityReserved() {
        return capacityReserved(proposedSlotId);
    }

    private int capacityReserved(UUID slotId) {
        return jdbcTemplate.queryForObject(
                "select capacity_reserved from booking_slots where id = ?", Integer.class, slotId);
    }

    private String reservationStatus() {
        return jdbcTemplate.queryForObject(
                "select status from reservations where id = ?", String.class, reservationId);
    }

    private String proposalStatus(UUID proposalId) {
        return jdbcTemplate.queryForObject(
                "select status from reservation_proposals where id = ?", String.class, proposalId);
    }

    private int eventCount(String type) {
        return jdbcTemplate.queryForObject(
                "select count(*) from reservation_events where reservation_id = ? and event_type = ?",
                Integer.class, reservationId, type);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
