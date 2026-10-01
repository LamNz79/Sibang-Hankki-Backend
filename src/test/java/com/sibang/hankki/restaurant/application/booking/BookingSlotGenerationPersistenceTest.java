package com.sibang.hankki.restaurant.application.booking;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.BookingSlot;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantBookingSettings;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.BookingSlotRepository;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantBookingSettingsRepository;
import com.sibang.hankki.restaurant.application.exception.BookingSettingsNotConfiguredException;
import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;
import com.sibang.hankki.restaurant.domain.booking.BookingSlotCandidate;
import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(BookingSlotGenerationPersistenceTest.FixedClockConfiguration.class)
@Transactional
@Rollback
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class BookingSlotGenerationPersistenceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);
    private static final LocalDate GENERATION_DATE = TODAY.plusDays(7);

    @Autowired
    private BookingSlotGenerationService generationService;

    @Autowired
    private RestaurantBookingSettingsRepository settingsRepository;

    @Autowired
    private BookingSlotRepository slotRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private UUID restaurantId;
    private LocalDate generationDate;

    @BeforeEach
    void preparePrototypeRestaurant() {
        restaurantId = jdbcTemplate.queryForObject(
                "select id from restaurants where slug = 'anan-saigon'", UUID.class);
        generationDate = GENERATION_DATE;
        OffsetDateTime start = timestamp(generationDate.atStartOfDay(BookingSlotGenerationService.RESTAURANT_TIME_ZONE).toInstant());
        OffsetDateTime end = timestamp(generationDate.plusDays(1).atStartOfDay(BookingSlotGenerationService.RESTAURANT_TIME_ZONE).toInstant());
        jdbcTemplate.update("delete from booking_slots where restaurant_id = ? and starts_at >= ? and starts_at < ?",
                restaurantId, start, end);
        jdbcTemplate.update("delete from restaurant_booking_settings where restaurant_id = ?", restaurantId);
    }

    @Test
    void generatesPersistedSlotsWithConfiguredCapacity() {
        settingsRepository.saveAndFlush(settings(20));

        List<BookingSlotCandidate> generated = generationService.generateSlots(restaurantId, generationDate, generationDate);
        List<BookingSlot> persisted = slotsInRange();

        assertEquals(10, generated.size());
        assertEquals(10, persisted.size());
        assertEquals(20, persisted.get(0).getCapacityTotal());
        assertEquals(0, persisted.get(0).getCapacityReserved());
    }

    @Test
    void runningGenerationTwiceDoesNotCreateDuplicates() {
        settingsRepository.saveAndFlush(settings(20));

        List<BookingSlotCandidate> firstRun = generationService.generateSlots(restaurantId, generationDate, generationDate);
        List<BookingSlotCandidate> secondRun = generationService.generateSlots(restaurantId, generationDate, generationDate);

        assertEquals(10, firstRun.size());
        assertEquals(List.of(), secondRun);
        assertEquals(10, slotsInRange().size());
    }

    @Test
    void runningGenerationAgainDoesNotOverwriteExistingCapacityValues() {
        settingsRepository.saveAndFlush(settings(20));
        List<BookingSlotCandidate> generated = generationService.generateSlots(restaurantId, generationDate, generationDate);
        entityManager.flush();
        Instant startsAt = generated.get(0).startsAt();
        assertEquals(1, jdbcTemplate.update("""
                update booking_slots
                set capacity_total = 99, capacity_reserved = 5
                where restaurant_id = ? and starts_at = ?
                """, restaurantId, timestamp(startsAt)));
        entityManager.clear();

        generationService.generateSlots(restaurantId, generationDate, generationDate);

        Integer capacityTotal = jdbcTemplate.queryForObject(
                "select capacity_total from booking_slots where restaurant_id = ? and starts_at = ?",
                Integer.class, restaurantId, timestamp(startsAt));
        Integer capacityReserved = jdbcTemplate.queryForObject(
                "select capacity_reserved from booking_slots where restaurant_id = ? and starts_at = ?",
                Integer.class, restaurantId, timestamp(startsAt));
        assertEquals(99, capacityTotal);
        assertEquals(5, capacityReserved);
    }

    @Test
    void reportsMissingSettingsForExistingRestaurant() {
        assertThrows(BookingSettingsNotConfiguredException.class,
                () -> generationService.generateSlots(restaurantId, generationDate, generationDate));
    }

    @Test
    void reportsUnknownRestaurant() {
        assertThrows(RestaurantNotFoundException.class,
                () -> generationService.generateSlots(UUID.randomUUID(), generationDate, generationDate));
    }

    private List<BookingSlot> slotsInRange() {
        Instant start = generationDate.atStartOfDay(BookingSlotGenerationService.RESTAURANT_TIME_ZONE).toInstant();
        Instant end = generationDate.plusDays(1).atStartOfDay(BookingSlotGenerationService.RESTAURANT_TIME_ZONE).toInstant();
        return slotRepository.findByRestaurantIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
                restaurantId, start, end);
    }

    private RestaurantBookingSettings settings(int capacity) {
        return new RestaurantBookingSettings(
                restaurantId, capacity, (short) 60, (short) 90, (short) 10, 30, ConfirmationMode.AUTO, null,
                (short) 30, (short) 1, (short) 6, (short) 7, 120, 15);
    }

    private OffsetDateTime timestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-09-28T03:00:00Z"), ZoneOffset.UTC);
        }
    }
}
