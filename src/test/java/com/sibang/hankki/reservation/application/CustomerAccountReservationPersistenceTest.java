package com.sibang.hankki.reservation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sibang.hankki.reservation.adapter.out.persistence.repository.ReservationEventJpaRepository;
import com.sibang.hankki.reservation.application.exception.ReservationNotFoundException;
import com.sibang.hankki.reservation.domain.model.ReservationStatus;
import com.sibang.hankki.reservation.domain.model.ReservationEventType;
import com.sibang.hankki.reservation.domain.model.VisitStatus;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.BookingSlotRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

@SpringBootTest
@Import(CustomerAccountReservationPersistenceTest.FixedClockConfiguration.class)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class CustomerAccountReservationPersistenceTest {

    private static final Instant FIRST_START = Instant.parse("2026-10-10T11:30:00Z");
    private static final Instant SECOND_START = Instant.parse("2026-10-11T11:30:00Z");

    @Autowired
    private CustomerReservationService service;
    @Autowired
    private OwnerReservationCommandService ownerReservationCommandService;
    @Autowired
    private BookingSlotRepository slotRepository;
    @Autowired
    private ReservationEventJpaRepository eventRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID restaurantId;
    private UUID customerId;
    private UUID otherCustomerId;
    private UUID actorId;
    private UUID slotId;
    private UUID firstReservationId;
    private UUID secondReservationId;
    private UUID otherReservationId;

    @BeforeEach
    void setUp() {
        restaurantId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        otherCustomerId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        firstReservationId = UUID.randomUUID();
        secondReservationId = UUID.randomUUID();
        otherReservationId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into restaurants (id, slug, name, city_slug, approval_status)
                values (?, ?, 'Account Reservation Test', 'ho-chi-minh-city', 'ACTIVE')
                """, restaurantId, "account-reservation-test-" + restaurantId);
        insertCustomer(customerId, "account-customer-");
        insertCustomer(otherCustomerId, "other-customer-");
        jdbcTemplate.update("""
                insert into users (id, userid, password_hash, name, role, status, restaurant_id)
                values (?, ?, 'unused-hash', 'Owner', 'OWNER', 'ACTIVE', ?)
                """, actorId, "check-in-owner-" + actorId.toString().substring(0, 8), restaurantId);
        jdbcTemplate.update("""
                insert into restaurant_booking_settings (
                    restaurant_id, guest_capacity, booking_interval_minutes, dining_duration_minutes,
                    checkout_hold_minutes, pending_expiry_minutes, confirmation_mode,
                    booking_window_days, minimum_party_size, maximum_online_party_size,
                    large_party_threshold, customer_cancellation_cutoff_minutes)
                values (?, 20, 60, 90, 10, 30, 'AUTO', 30, 1, 10, 11, 120)
                """, restaurantId);
        jdbcTemplate.update("""
                insert into booking_slots
                    (id, restaurant_id, starts_at, ends_at, capacity_total, capacity_reserved)
                values (?, ?, ?, ?, 4, 2)
                """, slotId, restaurantId, Timestamp.from(FIRST_START), Timestamp.from(FIRST_START.plusSeconds(5400)));
        insertReservation(firstReservationId, customerId, slotId, FIRST_START, "CONFIRMED", "EXPECTED");
        insertReservation(secondReservationId, customerId, null, SECOND_START, "PENDING", null);
        insertReservation(otherReservationId, otherCustomerId, null, SECOND_START.plusSeconds(3600), "PENDING", null);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from reservation_events where reservation_id in (?, ?, ?)",
                firstReservationId, secondReservationId, otherReservationId);
        jdbcTemplate.update("delete from reservations where restaurant_id = ?", restaurantId);
        jdbcTemplate.update("delete from booking_slots where restaurant_id = ?", restaurantId);
        jdbcTemplate.update("delete from restaurant_booking_settings where restaurant_id = ?", restaurantId);
        jdbcTemplate.update("delete from users where id in (?, ?, ?)", customerId, otherCustomerId, actorId);
        jdbcTemplate.update("delete from restaurants where id = ?", restaurantId);
    }

    @Test
    void listIsAccountScopedAndNewestVisitComesFirst() {
        var reservations = service.findAccountReservations(customerId);

        assertEquals(2, reservations.size());
        assertEquals(secondReservationId, reservations.get(0).reservation().id());
        assertEquals(firstReservationId, reservations.get(1).reservation().id());
        assertEquals("Account Reservation Test", reservations.get(0).restaurantName());
        assertThrows(ReservationNotFoundException.class,
                () -> service.findAccountReservation(otherReservationId, customerId));
    }

    @Test
    void accountCancellationReleasesConfirmedCapacityExactlyOnce() {
        assertEquals(ReservationStatus.CANCELLED,
                service.cancelAccountReservation(firstReservationId, customerId).reservation().status());
        assertEquals(ReservationStatus.CANCELLED,
                service.cancelAccountReservation(firstReservationId, customerId).reservation().status());

        assertEquals(0, slotRepository.findById(slotId).orElseThrow().getCapacityReserved());
        assertEquals(1, eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(firstReservationId).size());
    }

    @Test
    void suspendedCustomerCannotUseExistingSessionIdentity() {
        jdbcTemplate.update("update users set status = 'SUSPENDED' where id = ?", customerId);

        assertThrows(ReservationNotFoundException.class,
                () -> service.findAccountReservations(customerId));
    }

    @Test
    void suspendedRestaurantStillAllowsAccountHistoryAndCancellation() {
        jdbcTemplate.update("update restaurants set approval_status = 'SUSPENDED' where id = ?", restaurantId);

        assertEquals(2, service.findAccountReservations(customerId).size());
        assertEquals(firstReservationId,
                service.findAccountReservation(firstReservationId, customerId).reservation().id());
        assertEquals(ReservationStatus.CANCELLED,
                service.cancelAccountReservation(firstReservationId, customerId).reservation().status());
        assertEquals(0, slotRepository.findById(slotId).orElseThrow().getCapacityReserved());
    }

    @Test
    void rotatingTokenInvalidatesOldQrAndOwnerCheckInIsIdempotent() {
        String oldToken = service.issueAccountCheckInToken(firstReservationId, customerId);
        String currentToken = service.issueAccountCheckInToken(firstReservationId, customerId);

        String storedHash = jdbcTemplate.queryForObject(
                "select check_in_token_hash from reservations where id = ?",
                String.class,
                firstReservationId);
        assertNotEquals(oldToken, currentToken);
        assertNotEquals(currentToken, storedHash);
        assertEquals(ReservationToken.hash(currentToken), storedHash);

        assertThrows(ReservationNotFoundException.class,
                () -> ownerReservationCommandService.checkIn(oldToken, restaurantId, actorId));

        var arrived = ownerReservationCommandService.checkIn(currentToken, restaurantId, actorId);
        var replay = ownerReservationCommandService.checkIn(currentToken, restaurantId, actorId);
        assertEquals(VisitStatus.ARRIVED, arrived.visitStatus());
        assertEquals(FixedClockConfiguration.NOW, arrived.checkedInAt());
        assertEquals(actorId, arrived.checkedInBy());
        assertEquals(arrived.id(), replay.id());
        var events = eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(firstReservationId);
        assertEquals(1, events.size());
        assertEquals(ReservationEventType.CHECKED_IN, events.get(0).getEventType());
    }

    private void insertCustomer(UUID id, String prefix) {
        jdbcTemplate.update("""
                insert into users (id, userid, password_hash, name, role, status)
                values (?, ?, 'unused-hash', 'Customer', 'CUSTOMER', 'ACTIVE')
                """, id, prefix + id.toString().substring(0, 8));
    }

    private void insertReservation(
            UUID id, UUID ownerCustomerId, UUID bookingSlotId, Instant startsAt, String status, String visitStatus) {
        jdbcTemplate.update("""
                insert into reservations (
                    id, reference, idempotency_key, request_fingerprint, restaurant_id, booking_slot_id,
                    customer_id, customer_name, customer_phone, starts_at, ends_at, party_size, status, visit_status)
                values (?, ?, ?, ?, ?, ?, ?, 'Customer', '0900000000', ?, ?, 2, ?, ?)
                """, id, "SHK-" + id.toString().substring(0, 8), "account-" + id, "0".repeat(64),
                restaurantId, bookingSlotId, ownerCustomerId, Timestamp.from(startsAt),
                Timestamp.from(startsAt.plusSeconds(5400)), status, visitStatus);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
