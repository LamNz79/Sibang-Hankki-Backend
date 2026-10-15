package com.sibang.hankki.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.sibang.hankki.restaurant.application.model.BookingSettingsUpdate;
import com.sibang.hankki.restaurant.application.port.out.RestaurantBusinessHourData;
import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class SystemAdminBookingConfigurationPersistenceTest {

    @Autowired
    private SystemAdminBookingConfigurationService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesHoursAndGeneratesSlotsForDraftAndPendingRestaurantsInTheirTimezones() {
        verifyConfiguration("DRAFT", ZoneId.of("Asia/Seoul"));
        verifyConfiguration("PENDING", ZoneId.of("America/New_York"));
    }

    private void verifyConfiguration(String approvalStatus, ZoneId timeZone) {
        UUID restaurantId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into restaurants (id, slug, name, city_slug, timezone, approval_status)
                values (?, ?, ?, 'ho-chi-minh-city', ?, ?)
                """, restaurantId, "admin-config-" + restaurantId, "Admin config " + restaurantId,
                timeZone.getId(), approvalStatus);
        service.updateBookingSettings(restaurantId, new BookingSettingsUpdate(
                ConfirmationMode.AUTO, null, 20, 60, 90, 1, 1, 10, 11, 120, 15));

        LocalDate localDate = LocalDate.now(clock.withZone(timeZone));
        short dayOfWeek = (short) localDate.getDayOfWeek().getValue();
        var result = service.updateBusinessHours(restaurantId, List.of(
                new RestaurantBusinessHourData(
                        restaurantId, dayOfWeek, LocalTime.of(11, 0), LocalTime.of(13, 0))));
        entityManager.flush();

        assertThat(result.regeneration().generatedSlots()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from restaurant_business_hours where restaurant_id = ?",
                Integer.class, restaurantId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select starts_at from booking_slots where restaurant_id = ?",
                Timestamp.class, restaurantId).toInstant())
                .isEqualTo(localDate.atTime(11, 0).atZone(timeZone).toInstant());
    }
}
