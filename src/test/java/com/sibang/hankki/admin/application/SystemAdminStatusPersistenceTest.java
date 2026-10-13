package com.sibang.hankki.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import java.sql.Timestamp;
import java.time.Instant;
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
class SystemAdminStatusPersistenceTest {

    private static final Instant ORIGINAL_TIME = Instant.parse("2026-10-13T03:00:00Z");

    @Autowired
    private SystemAdminStatusService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void userStatusAndTimestampArePersistedAndAnIdempotentReplayDoesNotRewriteThem() {
        UUID userId = UUID.randomUUID();
        insertUser(userId, "status-" + userId, "CUSTOMER", "ACTIVE", null);

        var changed = service.updateUserStatus(userId, "SUSPENDED");
        Instant persistedTime = jdbcTemplate.queryForObject(
                "select status_changed_at from users where id = ?", Instant.class, userId);
        var replayed = service.updateUserStatus(userId, "SUSPENDED");

        assertThat(changed.status()).isEqualTo("SUSPENDED");
        assertThat(persistedTime).isAfter(ORIGINAL_TIME);
        assertThat(replayed.statusChangedAt()).isEqualTo(persistedTime);
        assertThat(jdbcTemplate.queryForObject(
                "select status from users where id = ?", String.class, userId)).isEqualTo("SUSPENDED");
    }

    @Test
    void restaurantStatusIsPersisted() {
        UUID restaurantId = UUID.randomUUID();
        insertRestaurant(restaurantId, "status-" + restaurantId, "PENDING", null);

        var changed = service.updateRestaurantStatus(restaurantId, "ACTIVE");

        assertThat(changed.approvalStatus()).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject(
                "select approval_status from restaurants where id = ?", String.class, restaurantId))
                .isEqualTo("ACTIVE");
    }

    @Test
    void softDeletedResourcesAreNotFound() {
        UUID userId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        insertUser(userId, "deleted-" + userId, "CUSTOMER", "ACTIVE", ORIGINAL_TIME);
        insertRestaurant(restaurantId, "deleted-" + restaurantId, "PENDING", ORIGINAL_TIME);

        assertThatThrownBy(() -> service.updateUserStatus(userId, "SUSPENDED"))
                .isInstanceOf(AdminResourceNotFoundException.class);
        assertThatThrownBy(() -> service.updateRestaurantStatus(restaurantId, "ACTIVE"))
                .isInstanceOf(AdminResourceNotFoundException.class);
    }

    private void insertUser(UUID id, String userid, String role, String status, Instant deletedAt) {
        jdbcTemplate.update("""
                insert into users (
                    id, userid, password_hash, name, role, status, status_changed_at,
                    deleted_at, created_at, updated_at
                ) values (?, ?, 'hash', 'Status Test', ?, ?, ?, ?, ?, ?)
                """,
                id, userid, role, status, Timestamp.from(ORIGINAL_TIME), timestamp(deletedAt),
                Timestamp.from(ORIGINAL_TIME), Timestamp.from(ORIGINAL_TIME));
    }

    private void insertRestaurant(UUID id, String slug, String status, Instant deletedAt) {
        jdbcTemplate.update("""
                insert into restaurants (
                    id, slug, name, city_slug, timezone, approval_status,
                    deleted_at, created_at, updated_at
                ) values (?, ?, 'Status Test', 'ho-chi-minh', 'Asia/Ho_Chi_Minh', ?, ?, ?, ?)
                """,
                id, slug, status, timestamp(deletedAt),
                Timestamp.from(ORIGINAL_TIME), Timestamp.from(ORIGINAL_TIME));
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
