package com.sibang.hankki.restaurant;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
@Rollback
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class RestaurantBookingSettingsPersistenceTest {

    @Autowired
    private RestaurantBookingSettingsRepository repository;

    @Autowired
    private RestaurantBookingSettingsService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID restaurantId;

    @BeforeEach
    void findPrototypeRestaurant() {
        restaurantId = jdbcTemplate.queryForObject(
                "select id from restaurants where slug = 'anan-saigon'", UUID.class);
    }

    @Test
    void persistsAndReadsValidSettingsByIdAndSlug() {
        repository.saveAndFlush(settings(ConfirmationMode.AUTO, null));

        assertEquals(restaurantId, service.getByRestaurantId(restaurantId).getRestaurantId());
        assertEquals(ConfirmationMode.AUTO, service.getByRestaurantSlug("anan-saigon").getConfirmationMode());
    }

    @Test
    void persistsAutoWithoutManualThreshold() {
        repository.saveAndFlush(settings(ConfirmationMode.AUTO, null));

        assertNull(repository.findById(restaurantId).orElseThrow().getManualConfirmationMinPartySize());
    }

    @Test
    void persistsManualWithoutManualThreshold() {
        repository.saveAndFlush(settings(ConfirmationMode.MANUAL, null));

        assertNull(repository.findById(restaurantId).orElseThrow().getManualConfirmationMinPartySize());
    }

    @Test
    void persistsHybridWithManualThreshold() {
        repository.saveAndFlush(settings(ConfirmationMode.HYBRID, (short) 7));

        assertEquals((short) 7, repository.findById(restaurantId).orElseThrow().getManualConfirmationMinPartySize());
    }

    @Test
    void rejectsHybridWithoutManualThreshold() {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(settings(ConfirmationMode.HYBRID, null)));
    }

    @Test
    void rejectsInvalidConfirmationModeInDatabase() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                insert into restaurant_booking_settings (
                    restaurant_id, guest_capacity, booking_interval_minutes, dining_duration_minutes,
                    checkout_hold_minutes, pending_expiry_minutes, confirmation_mode, booking_window_days,
                    minimum_party_size, maximum_online_party_size, large_party_threshold, created_at, updated_at
                ) values (?, 20, 15, 90, 10, 30, 'INVALID', 30, 1, 6, 7, current_timestamp, current_timestamp)
                """, restaurantId));
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(settingsWithCapacity(0)));
    }

    @Test
    void rejectsNonPositiveTimeValues() {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(settingsWithBookingInterval(0)));
    }

    @Test
    void reportsMissingSettings() {
        assertThrows(BookingSettingsNotConfiguredException.class,
                () -> service.getByRestaurantId(UUID.randomUUID()));
    }

    private RestaurantBookingSettings settings(ConfirmationMode mode, Short threshold) {
        return new RestaurantBookingSettings(
                restaurantId, 20, (short) 15, (short) 90, (short) 10, 30, mode, threshold,
                (short) 30, (short) 1, (short) 6, (short) 7, 120, 15);
    }

    private RestaurantBookingSettings settingsWithCapacity(int capacity) {
        return new RestaurantBookingSettings(
                restaurantId, capacity, (short) 15, (short) 90, (short) 10, 30, ConfirmationMode.AUTO, null,
                (short) 30, (short) 1, (short) 6, (short) 7, 120, 15);
    }

    private RestaurantBookingSettings settingsWithBookingInterval(int interval) {
        return new RestaurantBookingSettings(
                restaurantId, 20, (short) interval, (short) 90, (short) 10, 30, ConfirmationMode.AUTO, null,
                (short) 30, (short) 1, (short) 6, (short) 7, 120, 15);
    }
}
