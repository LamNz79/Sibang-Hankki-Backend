package com.sibang.hankki.restaurant;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
@Rollback
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class BookingSlotGenerationPersistenceTest {

    @Autowired
    private BookingSlotGenerationService generationService;

    @Autowired
    private RestaurantBookingSettingsRepository settingsRepository;

    @Autowired
    private BookingSlotRepository slotRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID restaurantId;
    private LocalDate generationDate;

    @BeforeEach
    void findPrototypeRestaurant() {
        restaurantId = jdbcTemplate.queryForObject(
                "select id from restaurants where slug = 'anan-saigon'", UUID.class);
        generationDate = LocalDate.of(2100, 1, 1)
                .plusDays(Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 36_500L));
    }

    @Test
    void generatesPersistedSlotsWithConfiguredCapacity() {
        settingsRepository.saveAndFlush(settings(20));

        List<BookingSlot> generated = generationService.generateSlots(restaurantId, generationDate, generationDate);

        assertEquals(10, generated.size());
        assertEquals(20, generated.get(0).getCapacityTotal());
        assertEquals(0, generated.get(0).getCapacityReserved());
        assertEquals(10, slotsInRange().size());
    }

    @Test
    void runningGenerationTwiceDoesNotCreateDuplicates() {
        settingsRepository.saveAndFlush(settings(20));

        List<BookingSlot> firstRun = generationService.generateSlots(restaurantId, generationDate, generationDate);
        List<BookingSlot> secondRun = generationService.generateSlots(restaurantId, generationDate, generationDate);

        assertEquals(10, firstRun.size());
        assertEquals(List.of(), secondRun);
        assertEquals(10, slotsInRange().size());
    }

    @Test
    void runningGenerationAgainDoesNotOverwriteExistingCapacityValues() {
        settingsRepository.saveAndFlush(settings(20));
        Instant startsAt = generationService.generateSlots(restaurantId, generationDate, generationDate)
                .get(0)
                .getStartsAt();
        jdbcTemplate.update("""
                update booking_slots
                set capacity_total = 99, capacity_reserved = 5
                where restaurant_id = ? and starts_at = ?
                """, restaurantId, startsAt);

        generationService.generateSlots(restaurantId, generationDate, generationDate);

        Integer capacityTotal = jdbcTemplate.queryForObject(
                "select capacity_total from booking_slots where restaurant_id = ? and starts_at = ?",
                Integer.class, restaurantId, startsAt);
        Integer capacityReserved = jdbcTemplate.queryForObject(
                "select capacity_reserved from booking_slots where restaurant_id = ? and starts_at = ?",
                Integer.class, restaurantId, startsAt);
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
}
