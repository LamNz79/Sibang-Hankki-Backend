package com.sibang.hankki.restaurant.application;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.BookingSlot;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantBookingSettings;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantEntity;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.BookingSlotRepository;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantBookingSettingsRepository;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantCatalogRepository;
import com.sibang.hankki.restaurant.application.booking.BookingSlotGenerationService;
import com.sibang.hankki.restaurant.application.exception.BookingSettingsNotConfiguredException;
import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;
import com.sibang.hankki.restaurant.application.model.RestaurantAvailabilityResponse;
import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Autowired
    private RestaurantCatalogRepository restaurantRepository;

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
        assertThrows(BookingSettingsNotConfiguredException.class,
                () -> availabilityService.availability("anan-saigon", DATE.toString(), 2));
    }

    @Test
    void reportsUnknownRestaurant() {
        assertThrows(RestaurantNotFoundException.class,
                () -> availabilityService.availability("missing-restaurant", DATE.toString(), 2));
    }

    @Test
    void treatsOnlyApprovedAndNonDeletedRestaurantsAsActive() {
        TestRestaurant active = insertRestaurant("ACTIVE", false);
        List<TestRestaurant> inactive = List.of(
                insertRestaurant("DRAFT", false),
                insertRestaurant("PENDING", false),
                insertRestaurant("CHANGES_REQUESTED", false),
                insertRestaurant("SUSPENDED", false),
                insertRestaurant("ACTIVE", true));

        List<UUID> activeIds = restaurantRepository.findAllActive().stream().map(RestaurantEntity::getId).toList();
        assertTrue(activeIds.contains(active.id()));
        for (TestRestaurant restaurant : inactive) {
            assertTrue(restaurantRepository.findActiveById(restaurant.id()).isEmpty());
            assertTrue(restaurantRepository.findActiveBySlug(restaurant.slug()).isEmpty());
            assertTrue(!activeIds.contains(restaurant.id()));
        }
    }

    @Test
    void doesNotExposeAvailabilityForInactiveRestaurants() {
        List<TestRestaurant> inactive = List.of(
                insertRestaurant("DRAFT", false),
                insertRestaurant("PENDING", false),
                insertRestaurant("CHANGES_REQUESTED", false),
                insertRestaurant("SUSPENDED", false),
                insertRestaurant("ACTIVE", true));

        for (TestRestaurant restaurant : inactive) {
            assertThrows(RestaurantNotFoundException.class,
                    () -> availabilityService.availability(restaurant.slug(), DATE.toString(), 2));
        }
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

    private TestRestaurant insertRestaurant(String approvalStatus, boolean deleted) {
        UUID id = UUID.randomUUID();
        String slug = "availability-test-" + id;
        if (deleted) {
            jdbcTemplate.update("""
                    insert into restaurants (id, slug, name, city_slug, approval_status, deleted_at)
                    values (?, ?, ?, ?, ?, current_timestamp)
                    """, id, slug, "Availability Test", "ho-chi-minh-city", approvalStatus);
        } else {
            jdbcTemplate.update("""
                    insert into restaurants (id, slug, name, city_slug, approval_status)
                    values (?, ?, ?, ?, ?)
                    """, id, slug, "Availability Test", "ho-chi-minh-city", approvalStatus);
        }
        return new TestRestaurant(id, slug);
    }

    private record TestRestaurant(UUID id, String slug) {
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
