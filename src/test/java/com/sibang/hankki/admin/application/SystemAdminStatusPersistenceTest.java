package com.sibang.hankki.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class SystemAdminStatusPersistenceTest {

    private static final Instant ORIGINAL_TIME = Instant.parse("2026-10-13T03:00:00Z");

    @Autowired
    private SystemAdminStatusService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void userStatusAndTimestampArePersistedAndAnIdempotentReplayDoesNotRewriteThem() {
        UUID userId = UUID.randomUUID();
        insertUser(userId, "status-" + shortId(userId), "CUSTOMER", "ACTIVE", null);

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
        insertUser(userId, "deleted-" + shortId(userId), "CUSTOMER", "ACTIVE", ORIGINAL_TIME);
        insertRestaurant(restaurantId, "deleted-" + restaurantId, "PENDING", ORIGINAL_TIME);

        assertThatThrownBy(() -> service.updateUserStatus(userId, "SUSPENDED"))
                .isInstanceOf(AdminResourceNotFoundException.class);
        assertThatThrownBy(() -> service.updateRestaurantStatus(restaurantId, "ACTIVE"))
                .isInstanceOf(AdminResourceNotFoundException.class);
    }

    @Test
    void suspendingAUserInvalidatesItsExistingAuthenticatedSession() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String ownerUserid = "owner-" + shortId(ownerId);
        String adminUserid = "admin-" + shortId(adminId);
        String password = "test-password";
        UUID restaurantId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        insertSessionUser(ownerId, ownerUserid, password, "OWNER", restaurantId);
        insertSessionUser(adminId, adminUserid, password, "ADMIN", null);
        MockHttpSession ownerSession = login(ownerUserid, password);
        MockHttpSession adminSession = login(adminUserid, password);

        mockMvc.perform(patch("/api/admin/users/{id}/status", ownerId)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").session(ownerSession))
                .andExpect(status().isUnauthorized());
        assertThat(ownerSession.isInvalid()).isTrue();
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

    private void insertSessionUser(
            UUID id,
            String userid,
            String password,
            String role,
            UUID restaurantId) {
        jdbcTemplate.update("""
                insert into users (
                    id, userid, password_hash, name, role, status, restaurant_id,
                    status_changed_at, created_at, updated_at
                ) values (?, ?, ?, 'Session Test', ?, 'ACTIVE', ?, ?, ?, ?)
                """,
                id, userid, passwordEncoder.encode(password), role, restaurantId,
                Timestamp.from(ORIGINAL_TIME), Timestamp.from(ORIGINAL_TIME), Timestamp.from(ORIGINAL_TIME));
    }

    private MockHttpSession login(String userid, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("userid", userid)
                        .param("password", password))
                .andExpect(status().isNoContent())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
