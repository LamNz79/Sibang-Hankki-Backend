package com.sibang.hankki.reservation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sibang.hankki.reservation.adapter.out.persistence.entity.ReservationEntity;
import com.sibang.hankki.reservation.adapter.out.persistence.repository.ReservationEventJpaRepository;
import com.sibang.hankki.reservation.adapter.out.persistence.repository.ReservationJpaRepository;
import com.sibang.hankki.reservation.application.exception.InvalidReservationRequestException;
import com.sibang.hankki.reservation.application.exception.ReservationCapacityUnavailableException;
import com.sibang.hankki.reservation.application.exception.ReservationIdempotencyConflictException;
import com.sibang.hankki.reservation.application.exception.ReservationNotFoundException;
import com.sibang.hankki.reservation.application.exception.ReservationSlotNotFoundException;
import com.sibang.hankki.reservation.application.port.in.CreateReservationCommand;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.BookingSlot;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantBookingSettings;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.BookingSlotRepository;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantBookingSettingsRepository;
import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
@Import(CreateReservationPersistenceTest.FixedClockConfiguration.class)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class CreateReservationPersistenceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 5);
    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired
    private CreateReservationService service;
    @Autowired
    private BookingSlotRepository slotRepository;
    @Autowired
    private RestaurantBookingSettingsRepository settingsRepository;
    @Autowired
    private ReservationJpaRepository reservationRepository;
    @Autowired
    private ReservationEventJpaRepository eventRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID restaurantId;
    private UUID slotId;

    @BeforeEach
    void setUp() {
        restaurantId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into restaurants (id, slug, name, city_slug, approval_status)
                values (?, ?, ?, ?, 'ACTIVE')
                """, restaurantId, slug(), "Reservation Test", "ho-chi-minh-city");
        settingsRepository.saveAndFlush(settings(ConfirmationMode.AUTO, null));
        slotId = slotAt("18:30", 4).getId();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from reservation_events where reservation_id in (select id from reservations where restaurant_id = ?)", restaurantId);
        jdbcTemplate.update("delete from reservations where restaurant_id = ?", restaurantId);
        jdbcTemplate.update("delete from booking_slots where restaurant_id = ?", restaurantId);
        jdbcTemplate.update("delete from restaurant_booking_settings where restaurant_id = ?", restaurantId);
        jdbcTemplate.update("delete from restaurants where id = ?", restaurantId);
    }

    @Test
    void autoReservationPersistsCapacityAndBothEvents() {
        var result = service.create(command("auto", 2, "18:30"));

        ReservationEntity reservation = reservationRepository.findById(result.reservation().id()).orElseThrow();
        assertEquals("CONFIRMED", reservation.getStatus().name());
        assertEquals("EXPECTED", reservation.getVisitStatus().name());
        assertEquals(2, slotRepository.findById(slotId).orElseThrow().getCapacityReserved());
        assertEquals(List.of("REQUESTED", "CONFIRMED"), eventRepository
                .findByReservationIdOrderByCreatedAtAscIdAsc(reservation.getId()).stream()
                .map(event -> event.getEventType().name()).toList());
    }

    @Test
    void manualAndHybridManualReservationsDoNotConsumeCapacity() {
        setMode(ConfirmationMode.MANUAL, null);
        var manual = service.create(command("manual", 2, "18:30"));
        assertEquals("PENDING", manual.reservation().status().name());
        assertEquals(0, slotRepository.findById(slotId).orElseThrow().getCapacityReserved());

        setMode(ConfirmationMode.HYBRID, (short) 3);
        BookingSlot hybridSlot = slotAt("19:30", 4);
        var automatic = service.create(command("hybrid-auto", 2, "19:30"));
        var pending = service.create(command("hybrid-pending", 3, "19:30"));
        assertEquals("CONFIRMED", automatic.reservation().status().name());
        assertEquals("PENDING", pending.reservation().status().name());
        assertEquals(2, slotRepository.findById(hybridSlot.getId()).orElseThrow().getCapacityReserved());
    }

    @Test
    void idempotencyAndValidationBehaveWithoutSideEffects() {
        var first = service.create(command("same", 2, "18:30"));
        var replay = service.create(command("same", 2, "18:30"));
        assertTrue(replay.idempotentReplay());
        assertEquals(first.reservation().id(), replay.reservation().id());
        assertEquals(2, slotRepository.findById(slotId).orElseThrow().getCapacityReserved());
        assertEquals(2, eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(first.reservation().id()).size());
        assertThrows(ReservationIdempotencyConflictException.class, () -> service.create(command("same", 3, "18:30")));
        assertThrows(InvalidReservationRequestException.class, () -> service.create(command("past", 2, "18:30", "2026-09-30")));
        assertThrows(ReservationNotFoundException.class, () -> service.create(command("missing", "missing", 2, "18:30")));
        assertThrows(ReservationSlotNotFoundException.class, () -> service.create(command("slot", 2, "17:30")));
    }

    @Test
    void insufficientAndConcurrentRequestsCannotOverbookTheSlot() throws Exception {
        BookingSlot smallSlot = slotAt("20:30", 2);
        assertThrows(ReservationCapacityUnavailableException.class, () -> service.create(command("too-many", 3, "20:30")));
        assertEquals(0, slotRepository.findById(smallSlot.getId()).orElseThrow().getCapacityReserved());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> first = () -> createConcurrently("concurrent-1");
            Callable<Boolean> second = () -> createConcurrently("concurrent-2");
            List<Future<Boolean>> results = executor.invokeAll(List.of(first, second));
            long successful = results.stream().filter(this::completedSuccessfully).count();
            assertEquals(1, successful);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(2, slotRepository.findById(smallSlot.getId()).orElseThrow().getCapacityReserved());
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from reservations where restaurant_id = ? and booking_slot_id = ?", Integer.class,
                restaurantId, smallSlot.getId()));
    }

    @Test
    void concurrentIdenticalIdempotencyRequestsReserveOnlyOnce() throws Exception {
        BookingSlot slot = slotAt("21:30", 4);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier startTogether = new CyclicBarrier(2);
        try {
            Callable<com.sibang.hankki.reservation.application.port.in.CreateReservationResult> first =
                    () -> {
                        startTogether.await();
                        return service.create(command("same-concurrent-key", 2, "21:30"));
                    };
            Callable<com.sibang.hankki.reservation.application.port.in.CreateReservationResult> second =
                    () -> {
                        startTogether.await();
                        return service.create(command("same-concurrent-key", 2, "21:30"));
                    };
            List<Future<com.sibang.hankki.reservation.application.port.in.CreateReservationResult>> results =
                    executor.invokeAll(List.of(first, second));
            var firstResult = results.get(0).get();
            var secondResult = results.get(1).get();

            assertEquals(firstResult.reservation().id(), secondResult.reservation().id());
            assertTrue(firstResult.idempotentReplay() != secondResult.idempotentReplay());
        } finally {
            executor.shutdownNow();
        }
        assertEquals(2, slotRepository.findById(slot.getId()).orElseThrow().getCapacityReserved());
        UUID reservationId = jdbcTemplate.queryForObject(
                "select id from reservations where restaurant_id = ? and booking_slot_id = ?", UUID.class,
                restaurantId, slot.getId());
        assertEquals(List.of("REQUESTED", "CONFIRMED"), eventRepository
                .findByReservationIdOrderByCreatedAtAscIdAsc(reservationId).stream()
                .map(event -> event.getEventType().name()).toList());
    }

    private boolean createConcurrently(String key) {
        try {
            service.create(command(key, 2, "20:30"));
            return true;
        } catch (ReservationCapacityUnavailableException exception) {
            return false;
        }
    }

    private boolean completedSuccessfully(Future<Boolean> result) {
        try {
            return result.get();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private void setMode(ConfirmationMode mode, Short threshold) {
        jdbcTemplate.update("""
                update restaurant_booking_settings
                set confirmation_mode = ?, manual_confirmation_min_party_size = ?
                where restaurant_id = ?
                """, mode.name(), threshold, restaurantId);
    }

    private RestaurantBookingSettings settings(ConfirmationMode mode, Short threshold) {
        return new RestaurantBookingSettings(
                restaurantId, 20, (short) 60, (short) 90, (short) 10, 30, mode, threshold,
                (short) 30, (short) 1, (short) 10, (short) 11, 120, 15);
    }

    private BookingSlot slotAt(String time, int capacity) {
        Instant startsAt = DATE.atTime(LocalTime.parse(time)).atZone(TIME_ZONE).toInstant();
        return slotRepository.saveAndFlush(new BookingSlot(
                restaurantId, startsAt, startsAt.plusSeconds(90 * 60L), capacity, 0));
    }

    private CreateReservationCommand command(String key, int partySize, String time) {
        return command(key, slug(), partySize, time);
    }

    private CreateReservationCommand command(String key, int partySize, String time, String date) {
        return new CreateReservationCommand(key, slug(), date, time, partySize, "Minh Lam", "0900000000", null, null, null);
    }

    private CreateReservationCommand command(String key, String restaurantSlug, int partySize, String time) {
        return new CreateReservationCommand(key, restaurantSlug, DATE.toString(), time, partySize, "Minh Lam", "0900000000", null, null, null);
    }

    private String slug() {
        return "reservation-test-" + restaurantId;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-01T03:00:00Z"), ZoneOffset.UTC);
        }
    }
}
