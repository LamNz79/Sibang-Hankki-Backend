package com.sibang.hankki.reservation.adapter.out.persistence;

import com.sibang.hankki.reservation.domain.model.Reservation;
import com.sibang.hankki.reservation.domain.model.ReservationEvent;
import com.sibang.hankki.reservation.domain.model.ReservationEventType;
import com.sibang.hankki.reservation.domain.model.ReservationStatus;
import com.sibang.hankki.reservation.domain.model.VisitStatus;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
@Rollback
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class ReservationPersistenceAdapterTest {

    private static final UUID PROTOTYPE_RESTAURANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant STARTS_AT = Instant.parse("2026-10-15T11:30:00Z");
    private static final Instant ENDS_AT = Instant.parse("2026-10-15T13:00:00Z");

    @Autowired
    private ReservationPersistenceAdapter reservationAdapter;

    @Autowired
    private ReservationEventPersistenceAdapter eventAdapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void flywayAppliesReservationFoundationMigration() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '7' and success = true", Integer.class);

        assertEquals(1, migrationCount);
    }

    @Test
    void persistsReadsAndMapsARegisteredCustomerReservation() {
        UUID customerId = insertUser();
        UUID bookingSlotId = insertBookingSlot(PROTOTYPE_RESTAURANT_ID);
        Reservation requested = reservation(
                UUID.randomUUID(), "RSV-REGISTERED-1", "idempotency-registered-1", PROTOTYPE_RESTAURANT_ID,
                bookingSlotId, customerId, ReservationStatus.CONFIRMED, VisitStatus.EXPECTED, true,
                "check-in-hash-registered-1");

        Reservation saved = reservationAdapter.save(requested);

        assertEquals(requested.id(), saved.id());
        assertEquals(requested.reference(), saved.reference());
        assertEquals(requested.idempotencyKey(), saved.idempotencyKey());
        assertEquals(requested.requestFingerprint(), saved.requestFingerprint());
        assertEquals(PROTOTYPE_RESTAURANT_ID, saved.restaurantId());
        assertEquals(bookingSlotId, saved.bookingSlotId());
        assertEquals(customerId, saved.customerId());
        assertEquals("Reservation guest", saved.customerName());
        assertEquals("guest@example.test", saved.customerEmail());
        assertEquals("0900000000", saved.customerPhone());
        assertEquals(STARTS_AT, saved.startsAt());
        assertEquals(ENDS_AT, saved.endsAt());
        assertEquals(2, saved.partySize());
        assertEquals(ReservationStatus.CONFIRMED, saved.status());
        assertEquals(VisitStatus.EXPECTED, saved.visitStatus());
        assertTrue(saved.capacityOverride());
        assertEquals("Window seat", saved.specialRequest());
        assertEquals("No peanuts", saved.preOrderNote());
        assertEquals("check-in-hash-registered-1", saved.checkInTokenHash());
        assertNotNull(saved.createdAt());
        assertNotNull(saved.updatedAt());
        assertEquals(0, saved.version());
        assertEquals(saved, reservationAdapter.findById(saved.id()).orElseThrow());
        assertEquals(saved, reservationAdapter.findByReference(saved.reference()).orElseThrow());
        assertEquals(saved, reservationAdapter.findByIdempotencyKey(saved.idempotencyKey()).orElseThrow());
        assertTrue(reservationAdapter.findByReference("unknown-reference").isEmpty());
        assertTrue(reservationAdapter.findByIdempotencyKey("unknown-idempotency-key").isEmpty());
    }

    @Test
    void supportsGuestReservationsWithoutCustomerIdOrBookingSlot() {
        Reservation saved = reservationAdapter.save(reservation(
                UUID.randomUUID(), "RSV-GUEST-1", "idempotency-guest-1", PROTOTYPE_RESTAURANT_ID,
                null, null, ReservationStatus.PENDING, null, false, null));

        assertNull(saved.customerId());
        assertNull(saved.bookingSlotId());
        assertEquals(ReservationStatus.PENDING, saved.status());
        assertNull(saved.visitStatus());
    }

    @Test
    void rejectsBookingSlotFromAnotherRestaurant() {
        UUID anotherRestaurantId = insertRestaurant();
        UUID bookingSlotId = insertBookingSlot(anotherRestaurantId, STARTS_AT, ENDS_AT);

        assertThrows(DataIntegrityViolationException.class, () -> reservationAdapter.save(reservation(
                UUID.randomUUID(), "RSV-WRONG-RESTAURANT", "idempotency-wrong-restaurant", PROTOTYPE_RESTAURANT_ID,
                bookingSlotId, null, ReservationStatus.PENDING, null, false, null)));
    }

    @Test
    void rejectsBookingSlotWithMismatchedStartOrEndTime() {
        UUID bookingSlotId = insertBookingSlot(PROTOTYPE_RESTAURANT_ID, STARTS_AT, ENDS_AT);

        assertDatabaseRejects(() -> insertRawReservationWithSlot(
                bookingSlotId, PROTOTYPE_RESTAURANT_ID, STARTS_AT.plusSeconds(60), ENDS_AT));
        assertDatabaseRejects(() -> insertRawReservationWithSlot(
                bookingSlotId, PROTOTYPE_RESTAURANT_ID, STARTS_AT, ENDS_AT.plusSeconds(60)));
    }

    @Test
    void enforcesUniqueReferenceIdempotencyKeyAndCheckInTokenHash() {
        insertRawReservation("RSV-UNIQUE-1", "idempotency-unique-1", "check-in-hash-unique",
                2, STARTS_AT, ENDS_AT, "PENDING", null, false);

        assertDatabaseRejects(() -> insertRawReservation(
                "RSV-UNIQUE-1", "idempotency-unique-2", "check-in-hash-unique-2",
                2, STARTS_AT, ENDS_AT, "PENDING", null, false));
        assertDatabaseRejects(() -> insertRawReservation(
                "RSV-UNIQUE-2", "idempotency-unique-1", "check-in-hash-unique-3",
                2, STARTS_AT, ENDS_AT, "PENDING", null, false));
        assertDatabaseRejects(() -> insertRawReservation(
                "RSV-UNIQUE-3", "idempotency-unique-3", "check-in-hash-unique",
                2, STARTS_AT, ENDS_AT, "PENDING", null, false));
    }

    @Test
    void enforcesDatabaseReservationStatusAndTimeInvariants() {
        assertRawReservationRejected(0, STARTS_AT, ENDS_AT, "PENDING", null, false);
        assertRawReservationRejected(2, ENDS_AT, STARTS_AT, "PENDING", null, false);
        assertRawReservationRejected(2, STARTS_AT, ENDS_AT, "REJECTED", null, false);
        assertRawReservationRejected(2, STARTS_AT, ENDS_AT, "PENDING", "EXPECTED", false);
        assertRawReservationRejected(2, STARTS_AT, ENDS_AT, "CONFIRMED", null, false);
        assertRawReservationRejected(2, STARTS_AT, ENDS_AT, "CONFIRMED", "UNKNOWN", false);
        assertRawReservationRejected(2, STARTS_AT, ENDS_AT, "PENDING", null, true);

        insertRawReservation(2, STARTS_AT, ENDS_AT, "CONFIRMED", "EXPECTED", true);
        insertRawReservation(2, STARTS_AT, ENDS_AT, "PENDING", null, false);
    }

    @Test
    void appliesOptimisticLockingVersion() {
        Reservation saved = reservationAdapter.save(reservation(
                UUID.randomUUID(), "RSV-VERSION-1", "idempotency-version-1", PROTOTYPE_RESTAURANT_ID,
                null, null, ReservationStatus.PENDING, null, false, null));
        Reservation updated = reservationAdapter.save(withSpecialRequest(saved, "Updated request"));
        assertEquals(1, updated.version());

        jdbcTemplate.update("update reservations set version = version + 1 where id = ?", updated.id());
        entityManager.clear();

        assertThrows(OptimisticLockingFailureException.class,
                () -> reservationAdapter.save(withSpecialRequest(updated, "Stale request")));
    }

    @Test
    void appendsAndReadsEventsInCreatedAtOrderAndEnforcesCommandIdUniqueness() {
        Reservation reservation = reservationAdapter.save(reservation(
                UUID.randomUUID(), "RSV-EVENT-1", "idempotency-event-1", PROTOTYPE_RESTAURANT_ID,
                null, null, ReservationStatus.PENDING, null, false, null));
        ReservationEvent requested = event(reservation.id(), ReservationEventType.REQUESTED,
                "command-requested", Instant.parse("2026-10-01T00:00:00Z"));
        ReservationEvent alternative = event(reservation.id(), ReservationEventType.ALTERNATIVE_PROPOSED,
                "command-alternative", Instant.parse("2026-10-01T00:00:01Z"));

        ReservationEvent appendedRequested = eventAdapter.append(requested);
        ReservationEvent appendedAlternative = eventAdapter.append(alternative);

        assertEquals(List.of(appendedRequested, appendedAlternative), eventAdapter.findByReservationId(reservation.id()));
        assertDatabaseRejects(() -> jdbcTemplate.update("""
                insert into reservation_events (id, reservation_id, event_type, command_id)
                values (?, ?, 'REQUESTED', 'command-requested')
                """, UUID.randomUUID(), reservation.id()));
        assertDatabaseRejects(() -> jdbcTemplate.update("""
                insert into reservation_events (id, reservation_id, event_type)
                values (?, ?, 'REJECTED')
                """, UUID.randomUUID(), reservation.id()));
    }

    @Test
    void enforcesRestrictForeignKeysForReferencedRows() {
        UUID restaurantId = insertRestaurant();
        Reservation restaurantReservation = reservationAdapter.save(reservation(
                UUID.randomUUID(), "RSV-RESTRICT-RESTAURANT", "idempotency-restrict-restaurant", restaurantId,
                null, null, ReservationStatus.PENDING, null, false, null));
        assertDatabaseRejects(() -> jdbcTemplate.update("delete from restaurants where id = ?", restaurantId));

        UUID customerId = insertUser();
        Reservation customerReservation = reservationAdapter.save(reservation(
                UUID.randomUUID(), "RSV-RESTRICT-CUSTOMER", "idempotency-restrict-customer", PROTOTYPE_RESTAURANT_ID,
                null, customerId, ReservationStatus.PENDING, null, false, null));
        assertDatabaseRejects(() -> jdbcTemplate.update("delete from users where id = ?", customerId));

        UUID bookingSlotId = insertBookingSlot(PROTOTYPE_RESTAURANT_ID);
        Reservation slotReservation = reservationAdapter.save(reservation(
                UUID.randomUUID(), "RSV-RESTRICT-SLOT", "idempotency-restrict-slot", PROTOTYPE_RESTAURANT_ID,
                bookingSlotId, null, ReservationStatus.PENDING, null, false, null));
        assertDatabaseRejects(() -> jdbcTemplate.update("delete from booking_slots where id = ?", bookingSlotId));

        eventAdapter.append(event(slotReservation.id(), ReservationEventType.REQUESTED,
                "command-restrict-event", Instant.parse("2026-10-01T00:00:00Z")));
        assertDatabaseRejects(() -> jdbcTemplate.update("delete from reservations where id = ?", slotReservation.id()));

        assertFalse(reservationAdapter.findById(restaurantReservation.id()).isEmpty());
        assertFalse(reservationAdapter.findById(customerReservation.id()).isEmpty());
    }

    private Reservation reservation(
            UUID id,
            String reference,
            String idempotencyKey,
            UUID restaurantId,
            UUID bookingSlotId,
            UUID customerId,
            ReservationStatus status,
            VisitStatus visitStatus,
            boolean capacityOverride,
            String checkInTokenHash) {
        return new Reservation(
                id,
                reference,
                idempotencyKey,
                "a".repeat(64),
                restaurantId,
                bookingSlotId,
                customerId,
                "Reservation guest",
                "guest@example.test",
                "0900000000",
                STARTS_AT,
                ENDS_AT,
                2,
                status,
                capacityOverride,
                visitStatus,
                "Window seat",
                "No peanuts",
                checkInTokenHash,
                null,
                null,
                0,
                null,
                null);
    }

    private Reservation withSpecialRequest(Reservation reservation, String specialRequest) {
        return new Reservation(
                reservation.id(), reservation.reference(), reservation.idempotencyKey(), reservation.requestFingerprint(),
                reservation.restaurantId(), reservation.bookingSlotId(), reservation.customerId(), reservation.customerName(),
                reservation.customerEmail(), reservation.customerPhone(), reservation.startsAt(), reservation.endsAt(),
                reservation.partySize(), reservation.status(), reservation.capacityOverride(), reservation.visitStatus(),
                specialRequest, reservation.preOrderNote(), reservation.checkInTokenHash(), reservation.checkedInAt(),
                reservation.checkedInBy(), reservation.version(), reservation.createdAt(), reservation.updatedAt());
    }

    private ReservationEvent event(
            UUID reservationId, ReservationEventType eventType, String commandId, Instant createdAt) {
        return new ReservationEvent(
                UUID.randomUUID(), reservationId, eventType, null, commandId, "b".repeat(64),
                "{\"source\":\"integration-test\"}", createdAt);
    }

    private void assertRawReservationRejected(
            int partySize,
            Instant startsAt,
            Instant endsAt,
            String status,
            String visitStatus,
            boolean capacityOverride) {
        assertDatabaseRejects(() -> insertRawReservation(
                partySize, startsAt, endsAt, status, visitStatus, capacityOverride));
    }

    private void insertRawReservation(
            int partySize,
            Instant startsAt,
            Instant endsAt,
            String status,
            String visitStatus,
            boolean capacityOverride) {
        UUID id = UUID.randomUUID();
        insertRawReservation("RAW-" + id.toString().substring(0, 8), "raw-idempotency-" + id, null,
                partySize, startsAt, endsAt, status, visitStatus, capacityOverride);
    }

    private void insertRawReservation(
            String reference,
            String idempotencyKey,
            String checkInTokenHash,
            int partySize,
            Instant startsAt,
            Instant endsAt,
            String status,
            String visitStatus,
            boolean capacityOverride) {
        jdbcTemplate.update("""
                insert into reservations (
                    id, reference, idempotency_key, request_fingerprint, restaurant_id,
                    customer_name, customer_phone, starts_at, ends_at, party_size,
                    status, visit_status, capacity_override, check_in_token_hash
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), reference, idempotencyKey, "c".repeat(64), PROTOTYPE_RESTAURANT_ID,
                "Raw reservation", "0900000001", databaseTimestamp(startsAt), databaseTimestamp(endsAt),
                partySize, status, visitStatus, capacityOverride, checkInTokenHash);
    }

    private void insertRawReservationWithSlot(
            UUID bookingSlotId, UUID restaurantId, Instant startsAt, Instant endsAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into reservations (
                    id, reference, idempotency_key, request_fingerprint, restaurant_id, booking_slot_id,
                    customer_name, customer_phone, starts_at, ends_at, party_size, status, capacity_override
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', false)
                """, id, "RAW-SLOT-" + id.toString().substring(0, 8), "raw-slot-idempotency-" + id,
                "d".repeat(64), restaurantId, bookingSlotId, "Raw reservation", "0900000002",
                databaseTimestamp(startsAt), databaseTimestamp(endsAt), 2);
    }

    private void assertDatabaseRejects(Runnable operation) {
        String savepoint = "reservation_constraint_check";
        jdbcTemplate.execute("savepoint " + savepoint);
        try {
            operation.run();
            fail("Expected the database to reject the mutation");
        } catch (DataIntegrityViolationException expected) {
            // Expected: the savepoint preserves the outer test transaction for the next assertion.
        } finally {
            jdbcTemplate.execute("rollback to savepoint " + savepoint);
            jdbcTemplate.execute("release savepoint " + savepoint);
        }
    }

    private UUID insertRestaurant() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into restaurants (id, slug, name, city_slug, approval_status)
                values (?, ?, ?, ?, 'ACTIVE')
                """, id, "reservation-test-" + id, "Reservation Test", "ho-chi-minh-city");
        return id;
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into users (id, userid, password_hash, name)
                values (?, ?, ?, ?)
                """, id, "rsv-" + id.toString().substring(0, 8), "password-hash", "Reservation User");
        return id;
    }

    private UUID insertBookingSlot(UUID restaurantId) {
        return insertBookingSlot(restaurantId, STARTS_AT, ENDS_AT);
    }

    private UUID insertBookingSlot(UUID restaurantId, Instant startsAt, Instant endsAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into booking_slots (id, restaurant_id, starts_at, ends_at, capacity_total, capacity_reserved)
                values (?, ?, ?, ?, ?, ?)
                """, id, restaurantId, databaseTimestamp(startsAt), databaseTimestamp(endsAt), 10, 0);
        return id;
    }

    private java.time.OffsetDateTime databaseTimestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
