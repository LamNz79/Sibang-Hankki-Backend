package com.sibang.hankki.restaurant;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(RestaurantAvailabilityPersistenceTest.FixedClockConfiguration.class)
@Transactional
@Rollback
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class RestaurantAvailabilityPersistenceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);
    private static final LocalDate DATE = TODAY.plusDays(7);

    @Autowired
    private RestaurantAvailabilityService availabilityService;

    @Autowired
    private RestaurantBookingSettingsRepository settingsRepository;

    @Autowired
    private BookingSlotRepository slotRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID restaurantId;

    @BeforeEach
    void prepareRestaurant() {
        restaurantId = jdbcTemplate.queryForObject(
                "select id from restaurants where slug = 'anan-saigon'", UUID.class);
        Instant start = startOfDay(DATE);
        Instant end = startOfDay(DATE.plusDays(1));
        jdbcTemplate.update("delete from booking_slots where restaurant_id = ? and starts_at >= ? and starts_at < ?",
                restaurantId, timestamp(start), timestamp(end));
        jdbcTemplate.update("delete from restaurant_booking_settings where restaurant_id = ?", restaurantId);
    }

    @Test
    void returnsPersistedSlotsInLocalTimeOrder() {
        settingsRepository.saveAndFlush(settings(ConfirmationMode.AUTO, null));
        saveSlot("12:30", 2, 0);
        saveSlot("11:30", 2, 0);

        RestaurantAvailabilityResponse response = availabilityService.availability("anan-saigon", DATE.toString(), 2);

        assertEquals(List.of("11:30", "12:30"), response.slots());
        assertEquals(false, response.requiresRestaurantConfirmation());
    }

    @Test
    void includesExactCapacityButExcludesInsufficientAndFullyReservedSlots() {
        settingsRepository.saveAndFlush(settings(ConfirmationMode.AUTO, null));
        saveSlot("11:30", 2, 0);
        saveSlot("12:30", 4, 3);
        saveSlot("13:30", 4, 4);

        RestaurantAvailabilityResponse response = availabilityService.availability("anan-saigon", DATE.toString(), 2);

        assertEquals(List.of("11:30"), response.slots());
    }

    @Test
    void returnsNoSlotsWhenTheDateHasNoPersistedSlots() {
        settingsRepository.saveAndFlush(settings(ConfirmationMode.AUTO, null));

        RestaurantAvailabilityResponse response = availabilityService.availability("anan-saigon", DATE.toString(), 2);

        assertEquals(List.of(), response.slots());
    }

    @Test
    void reportsMissingSettingsForAnExistingRestaurant() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> availabilityService.availability("anan-saigon", DATE.toString(), 2));

        assertEquals(409, exception.getStatusCode().value());
    }

    @Test
    void reportsUnknownRestaurant() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> availabilityService.availability("missing-restaurant", DATE.toString(), 2));

        assertEquals(404, exception.getStatusCode().value());
    }

    private RestaurantBookingSettings settings(ConfirmationMode mode, Short manualConfirmationMinPartySize) {
        return new RestaurantBookingSettings(
                restaurantId, 20, (short) 60, (short) 90, (short) 10, 30, mode,
                manualConfirmationMinPartySize, (short) 30, (short) 1, (short) 6, (short) 7, 120, 15);
    }

    private void saveSlot(String localTime, int capacityTotal, int capacityReserved) {
        Instant startsAt = DATE.atTime(LocalTime.parse(localTime))
                .atZone(BookingSlotGenerationService.RESTAURANT_TIME_ZONE)
                .toInstant();
        slotRepository.saveAndFlush(new BookingSlot(
                restaurantId, startsAt, startsAt.plusSeconds(90 * 60L), capacityTotal, capacityReserved));
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(BookingSlotGenerationService.RESTAURANT_TIME_ZONE).toInstant();
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
