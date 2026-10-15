package com.sibang.hankki.restaurant.application;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantBookingSettings;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantBookingSettingsRepository;
import com.sibang.hankki.restaurant.application.exception.BookingSettingsNotConfiguredException;
import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;
import com.sibang.hankki.restaurant.application.model.BookingSettingsUpdate;
import com.sibang.hankki.restaurant.application.model.OwnerSettingsUpdate;
import com.sibang.hankki.restaurant.application.port.out.OwnerSettingsPort;
import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;

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
    private OwnerSettingsPort ownerSettingsPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID restaurantId;

    @BeforeEach
    void findPrototypeRestaurant() {
        restaurantId = jdbcTemplate.queryForObject(
                "select id from restaurants where slug = 'anan-saigon'", UUID.class);
        jdbcTemplate.update("delete from restaurant_booking_settings where restaurant_id = ?", restaurantId);
    }

    @Test
    void persistsAndReadsValidSettingsByIdAndSlug() {
        repository.saveAndFlush(settings(ConfirmationMode.AUTO, null));

        assertEquals(restaurantId, service.getByRestaurantId(restaurantId).restaurantId());
        assertEquals(ConfirmationMode.AUTO, service.getByRestaurantSlug("anan-saigon").confirmationMode());
        assertEquals(15, service.getByRestaurantId(restaurantId).noShowGraceMinutes());
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
    void ownerUpdatePersistsNoShowGraceWithoutOverwritingInternalSettings() {
        repository.saveAndFlush(settings(ConfirmationMode.AUTO, null));

        ownerSettingsPort.update(restaurantId, new OwnerSettingsUpdate(
                "Anan Saigon", null, null, null, null, null, null, null, null,
                20, 15, 90, ConfirmationMode.AUTO, null, 30, 1, 6, 7, 120, 0));
        repository.flush();

        RestaurantBookingSettings updated = repository.findById(restaurantId).orElseThrow();
        assertEquals(0, updated.getNoShowGraceMinutes());
        assertEquals(10, jdbcTemplate.queryForObject(
                "select checkout_hold_minutes from restaurant_booking_settings where restaurant_id = ?",
                Integer.class, restaurantId));
        assertEquals(30, jdbcTemplate.queryForObject(
                "select pending_expiry_minutes from restaurant_booking_settings where restaurant_id = ?",
                Integer.class, restaurantId));
        assertEquals(120, updated.getCustomerCancellationCutoffMinutes());
    }

    @Test
    void administrationUpdateRoundTripsExplicitNullAndZeroWithoutChangingAnotherRestaurant() {
        repository.saveAndFlush(settings(ConfirmationMode.AUTO, null));
        UUID otherRestaurantId = jdbcTemplate.queryForObject(
                "select id from restaurants where slug = 'royal-pavilion'", UUID.class);
        Integer otherGrace = jdbcTemplate.queryForObject(
                "select no_show_grace_minutes from restaurant_booking_settings where restaurant_id = ?",
                Integer.class, otherRestaurantId);

        ownerSettingsPort.updateForAdministration(restaurantId, new BookingSettingsUpdate(
                ConfirmationMode.AUTO, null, 24, 30, 90, 30, 1, 8, 10, null, null));
        repository.flush();
        RestaurantBookingSettings withNulls = repository.findById(restaurantId).orElseThrow();
        assertNull(withNulls.getCustomerCancellationCutoffMinutes());
        assertNull(withNulls.getNoShowGraceMinutes());

        ownerSettingsPort.updateForAdministration(restaurantId, new BookingSettingsUpdate(
                ConfirmationMode.AUTO, null, 24, 30, 90, 30, 1, 8, 10, 0, 0));
        repository.flush();
        RestaurantBookingSettings withZeroes = repository.findById(restaurantId).orElseThrow();
        assertEquals(0, withZeroes.getCustomerCancellationCutoffMinutes());
        assertEquals(0, withZeroes.getNoShowGraceMinutes());
        assertEquals(otherGrace, jdbcTemplate.queryForObject(
                "select no_show_grace_minutes from restaurant_booking_settings where restaurant_id = ?",
                Integer.class, otherRestaurantId));
    }

    @Test
    void administrationUpdateInitializesMissingBookingSettings() {
        ownerSettingsPort.updateForAdministration(restaurantId, new BookingSettingsUpdate(
                ConfirmationMode.HYBRID, 7, 20, 60, 90, 30, 1, 10, 11, 120, 15));
        repository.flush();

        RestaurantBookingSettings created = repository.findById(restaurantId).orElseThrow();
        assertEquals(ConfirmationMode.HYBRID, created.getConfirmationMode());
        assertEquals((short) 7, created.getManualConfirmationMinPartySize());
        assertEquals(10, jdbcTemplate.queryForObject(
                "select checkout_hold_minutes from restaurant_booking_settings where restaurant_id = ?",
                Integer.class, restaurantId));
        assertEquals(30, jdbcTemplate.queryForObject(
                "select pending_expiry_minutes from restaurant_booking_settings where restaurant_id = ?",
                Integer.class, restaurantId));
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
    void reportsSettingsNotConfiguredForExistingRestaurant() {
        assertThrows(BookingSettingsNotConfiguredException.class,
                () -> service.getByRestaurantSlug("anan-saigon"));
    }

    @Test
    void reportsRestaurantNotFoundBySlug() {
        assertThrows(RestaurantNotFoundException.class,
                () -> service.getByRestaurantSlug("unknown"));
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
