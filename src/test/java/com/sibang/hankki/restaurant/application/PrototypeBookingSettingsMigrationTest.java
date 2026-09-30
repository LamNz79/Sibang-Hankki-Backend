package com.sibang.hankki.restaurant.application;
import com.sibang.hankki.restaurant.application.booking.BookingSlotGenerationService;
import com.sibang.hankki.restaurant.application.model.RestaurantAvailabilityResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@Import(RestaurantAvailabilityPersistenceTest.FixedClockConfiguration.class)
@Transactional
@Rollback
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class PrototypeBookingSettingsMigrationTest {

    private static final List<String> PROTOTYPE_SLUGS = List.of(
            "anan-saigon", "royal-pavilion", "refinery", "mori-teppan", "hanoi-hearth", "han-river-dining");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RestaurantAvailabilityService availabilityService;

    @Test
    void seedsExactlyOneDefaultSettingsRowForEachPrototypeRestaurant() {
        jdbcTemplate.update("""
                delete from restaurant_booking_settings
                where restaurant_id in (select id from restaurants where slug in (?, ?, ?, ?, ?, ?))
                """, PROTOTYPE_SLUGS.toArray());
        executeV6();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select r.slug, s.guest_capacity, s.booking_interval_minutes, s.dining_duration_minutes,
                    s.checkout_hold_minutes, s.pending_expiry_minutes, s.confirmation_mode,
                    s.manual_confirmation_min_party_size, s.booking_window_days, s.minimum_party_size,
                    s.maximum_online_party_size, s.large_party_threshold,
                    s.customer_cancellation_cutoff_minutes, s.no_show_grace_minutes
                from restaurants r
                join restaurant_booking_settings s on s.restaurant_id = r.id
                where r.slug in (?, ?, ?, ?, ?, ?)
                order by r.slug
                """, PROTOTYPE_SLUGS.toArray());

        assertEquals(6, rows.size());
        assertEquals(Set.copyOf(PROTOTYPE_SLUGS),
                rows.stream().map(row -> (String) row.get("slug")).collect(java.util.stream.Collectors.toSet()));
        for (Map<String, Object> row : rows) {
            assertEquals(20, number(row, "guest_capacity"));
            assertEquals(60, number(row, "booking_interval_minutes"));
            assertEquals(90, number(row, "dining_duration_minutes"));
            assertEquals(10, number(row, "checkout_hold_minutes"));
            assertEquals(30, number(row, "pending_expiry_minutes"));
            assertEquals("HYBRID", row.get("confirmation_mode"));
            assertEquals(7, number(row, "manual_confirmation_min_party_size"));
            assertEquals(30, number(row, "booking_window_days"));
            assertEquals(1, number(row, "minimum_party_size"));
            assertEquals(10, number(row, "maximum_online_party_size"));
            assertEquals(11, number(row, "large_party_threshold"));
            assertEquals(120, number(row, "customer_cancellation_cutoff_minutes"));
            assertEquals(15, number(row, "no_show_grace_minutes"));
        }
    }

    @Test
    void doesNotOverwriteAnExistingSettingsRowWhenTheMigrationRunsAgain() {
        UUID restaurantId = jdbcTemplate.queryForObject(
                "select id from restaurants where slug = 'anan-saigon'", UUID.class);
        jdbcTemplate.update("delete from restaurant_booking_settings where restaurant_id = ?", restaurantId);
        jdbcTemplate.update("""
                insert into restaurant_booking_settings (
                    restaurant_id, guest_capacity, booking_interval_minutes, dining_duration_minutes,
                    checkout_hold_minutes, pending_expiry_minutes, confirmation_mode, booking_window_days,
                    minimum_party_size, maximum_online_party_size, large_party_threshold,
                    customer_cancellation_cutoff_minutes, no_show_grace_minutes
                ) values (?, 99, 15, 60, 5, 20, 'AUTO', 14, 2, 8, 9, 30, 5)
                """, restaurantId);

        executeV6();

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                select guest_capacity, booking_interval_minutes, dining_duration_minutes, checkout_hold_minutes,
                    pending_expiry_minutes, confirmation_mode, manual_confirmation_min_party_size,
                    booking_window_days, minimum_party_size, maximum_online_party_size, large_party_threshold,
                    customer_cancellation_cutoff_minutes, no_show_grace_minutes
                from restaurant_booking_settings where restaurant_id = ?
                """, restaurantId);
        assertEquals(99, number(row, "guest_capacity"));
        assertEquals(15, number(row, "booking_interval_minutes"));
        assertEquals(60, number(row, "dining_duration_minutes"));
        assertEquals(5, number(row, "checkout_hold_minutes"));
        assertEquals(20, number(row, "pending_expiry_minutes"));
        assertEquals("AUTO", row.get("confirmation_mode"));
        assertNull(row.get("manual_confirmation_min_party_size"));
        assertEquals(14, number(row, "booking_window_days"));
        assertEquals(2, number(row, "minimum_party_size"));
        assertEquals(8, number(row, "maximum_online_party_size"));
        assertEquals(9, number(row, "large_party_threshold"));
        assertEquals(30, number(row, "customer_cancellation_cutoff_minutes"));
        assertEquals(5, number(row, "no_show_grace_minutes"));
    }

    @Test
    void returnsEmptySlotsForAConfiguredPrototypeRestaurantWithoutSlots() {
        LocalDate date = LocalDate.of(2026, 9, 29);
        UUID restaurantId = jdbcTemplate.queryForObject(
                "select id from restaurants where slug = 'anan-saigon'", UUID.class);
        jdbcTemplate.update("delete from restaurant_booking_settings where restaurant_id = ?", restaurantId);
        executeV6();
        Instant start = date.atStartOfDay(BookingSlotGenerationService.RESTAURANT_TIME_ZONE).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(BookingSlotGenerationService.RESTAURANT_TIME_ZONE).toInstant();
        jdbcTemplate.update("delete from booking_slots where restaurant_id = ? and starts_at >= ? and starts_at < ?",
                restaurantId, timestamp(start), timestamp(end));

        RestaurantAvailabilityResponse response = assertDoesNotThrow(
                () -> availabilityService.availability("anan-saigon", date.toString(), 2));

        assertEquals(List.of(), response.slots());
        assertEquals(false, response.requiresRestaurantConfirmation());
    }

    private int number(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).intValue();
    }

    private void executeV6() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new ClassPathResource("db/migration/V6__seed_prototype_booking_settings.sql")));
            return null;
        });
    }

    private OffsetDateTime timestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
