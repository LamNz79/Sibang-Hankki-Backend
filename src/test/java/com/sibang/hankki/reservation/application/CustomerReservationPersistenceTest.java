package com.sibang.hankki.reservation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sibang.hankki.reservation.adapter.out.persistence.repository.ReservationEventJpaRepository;
import com.sibang.hankki.reservation.adapter.out.persistence.repository.ReservationJpaRepository;
import com.sibang.hankki.reservation.application.exception.ReservationNotFoundException;
import com.sibang.hankki.reservation.domain.model.ReservationStatus;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.BookingSlotRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
@Import(CustomerReservationPersistenceTest.FixedClockConfiguration.class)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class CustomerReservationPersistenceTest {

    private static final String TOKEN = "guest-management-token";
    private static final String TOKEN_HASH = ReservationManagementToken.hash(TOKEN);
    private static final Instant STARTS_AT = Instant.parse("2026-10-10T11:30:00Z");
    private static final Instant ENDS_AT = Instant.parse("2026-10-10T13:00:00Z");

    @Autowired
    private CustomerReservationService service;
    @Autowired
    private ReservationJpaRepository reservationRepository;
    @Autowired
    private ReservationEventJpaRepository eventRepository;
    @Autowired
    private BookingSlotRepository slotRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID restaurantId;
    private UUID slotId;
    private UUID reservationId;

    @BeforeEach
    void setUp() {
        restaurantId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        reservationId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into restaurants (id, slug, name, city_slug, approval_status)
                values (?, ?, 'Guest Access Test', 'ho-chi-minh-city', 'ACTIVE')
                """, restaurantId, "guest-access-test-" + restaurantId);
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
                """, slotId, restaurantId, Timestamp.from(STARTS_AT), Timestamp.from(ENDS_AT));
        jdbcTemplate.update("""
                insert into reservations (
                    id, reference, idempotency_key, request_fingerprint, restaurant_id, booking_slot_id,
                    customer_name, customer_phone, starts_at, ends_at, party_size, status, visit_status,
                    management_token_hash)
                values (?, ?, ?, ?, ?, ?, 'Minh Lam', '0900000000', ?, ?, 2, 'CONFIRMED', 'EXPECTED', ?)
                """, reservationId, "SHK-" + reservationId.toString().substring(0, 8),
                "guest-access-" + reservationId, "0".repeat(64), restaurantId, slotId,
                Timestamp.from(STARTS_AT), Timestamp.from(ENDS_AT), TOKEN_HASH);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from reservation_events where reservation_id = ?", reservationId);
        jdbcTemplate.update("delete from reservations where restaurant_id = ?", restaurantId);
        jdbcTemplate.update("delete from booking_slots where restaurant_id = ?", restaurantId);
        jdbcTemplate.update("delete from restaurant_booking_settings where restaurant_id = ?", restaurantId);
        jdbcTemplate.update("delete from restaurants where id = ?", restaurantId);
    }

    @Test
    void validTokenReadsReservationAndInvalidIdentifiersReturnNotFound() {
        assertEquals(ReservationStatus.CONFIRMED, service.findById(reservationId, TOKEN).status());
        assertThrows(ReservationNotFoundException.class, () -> service.findById(reservationId, "wrong"));
        assertThrows(ReservationNotFoundException.class, () -> service.findById(UUID.randomUUID(), TOKEN));
    }

    @Test
    void concurrentCancellationReleasesCapacityAndAppendsEventExactlyOnce() throws Exception {
        CyclicBarrier startTogether = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<UUID> cancel = () -> {
                startTogether.await();
                return service.cancel(reservationId, TOKEN).id();
            };
            List<Future<UUID>> results = executor.invokeAll(List.of(cancel, cancel));
            assertEquals(reservationId, results.get(0).get());
            assertEquals(reservationId, results.get(1).get());
        } finally {
            executor.shutdownNow();
        }

        assertEquals(ReservationStatus.CANCELLED,
                reservationRepository.findById(reservationId).orElseThrow().getStatus());
        assertNull(reservationRepository.findById(reservationId).orElseThrow().getVisitStatus());
        assertEquals(0, slotRepository.findById(slotId).orElseThrow().getCapacityReserved());
        var events = eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(reservationId);
        assertEquals(1, events.size());
        assertEquals("CANCELLED_BY_CUSTOMER", events.get(0).getEventType().name());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneOffset.UTC);
        }
    }
}
