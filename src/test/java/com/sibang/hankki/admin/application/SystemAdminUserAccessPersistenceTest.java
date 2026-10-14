package com.sibang.hankki.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.exception.AdminUserConflictException;
import com.sibang.hankki.admin.application.port.in.RestaurantAssignmentUseCase.AssignmentCommand;
import com.sibang.hankki.admin.application.port.in.SystemAdminUserUseCase.CreateUserCommand;
import com.sibang.hankki.admin.application.port.in.SystemAdminUserUseCase.UpdateUserCommand;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class SystemAdminUserAccessPersistenceTest {

    private final java.util.Set<UUID> userIds = new java.util.HashSet<>();
    private final java.util.Set<UUID> restaurantIds = new java.util.HashSet<>();

    @Autowired
    private SystemAdminUserService userService;

    @Autowired
    private RestaurantAssignmentService assignmentService;

    @Autowired
    private SystemAdminReadService readService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        userIds.forEach(id -> jdbcTemplate.update("delete from users where id = ?", id));
        restaurantIds.forEach(id -> jdbcTemplate.update("delete from restaurants where id = ?", id));
    }

    @Test
    void customerCrudAndAssignmentArePersistedWithoutExposingPassword() {
        UUID restaurantId = insertRestaurant("DRAFT", false);
        String userid = "admin-created-" + shortId(UUID.randomUUID());
        String password = "CustomerTest@2026!";

        var created = userService.create(new CreateUserCommand(
                userid, "customer@example.com", password, "Customer Name", "0901234567"));
        userIds.add(created.id());
        var updated = userService.update(
                created.id(), new UpdateUserCommand("updated@example.com", "Updated Name", "0909999999"));

        assertThat(userService.findById(created.id()).userid()).isEqualTo(userid);
        assertThat(updated.name()).isEqualTo("Updated Name");
        assertThat(passwordEncoder.matches(password, jdbcTemplate.queryForObject(
                "select password_hash from users where id = ?", String.class, created.id()))).isTrue();
        assertThatThrownBy(() -> userService.create(new CreateUserCommand(
                userid, null, password, "Duplicate", null)))
                .isInstanceOf(AdminUserConflictException.class);

        var assignment = assignmentService.create(new AssignmentCommand(created.id(), restaurantId, "OWNER"));
        var listed = assignmentService.findAll(0, 20, "Updated Name", "OWNER", restaurantId);
        var users = readService.findUsers(0, 20, userid, null, null);

        assertThat(assignment.restaurantStatus()).isEqualTo("DRAFT");
        assertThat(listed.items()).extracting(item -> item.userId()).containsExactly(created.id());
        assertThat(users.items().get(0).restaurantName()).isEqualTo("Restaurant " + shortId(restaurantId));

        assignmentService.remove(created.id());
        assertThat(jdbcTemplate.queryForObject("select role from users where id = ?", String.class, created.id()))
                .isEqualTo("CUSTOMER");
        assertThat(jdbcTemplate.queryForObject(
                "select restaurant_id from users where id = ?", UUID.class, created.id())).isNull();
    }

    @Test
    void missingOrDeletedRestaurantIsRejected() {
        var user = userService.create(new CreateUserCommand(
                "missing-" + shortId(UUID.randomUUID()), null, "CustomerTest@2026!", "Customer", null));
        userIds.add(user.id());
        UUID deletedRestaurant = insertRestaurant("ACTIVE", true);

        assertThatThrownBy(() -> assignmentService.create(
                new AssignmentCommand(user.id(), deletedRestaurant, "OWNER")))
                .isInstanceOf(AdminResourceNotFoundException.class);
    }

    @Test
    void concurrentAssignmentOnlyAllowsOneWinner() throws Exception {
        var user = userService.create(new CreateUserCommand(
                "concurrent-" + shortId(UUID.randomUUID()), null, "CustomerTest@2026!", "Customer", null));
        userIds.add(user.id());
        UUID firstRestaurant = insertRestaurant("ACTIVE", false);
        UUID secondRestaurant = insertRestaurant("SUSPENDED", false);
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> tasks = List.of(
                    () -> assign(user.id(), firstRestaurant),
                    () -> assign(user.id(), secondRestaurant));
            var results = executor.invokeAll(tasks).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }).toList();

            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(jdbcTemplate.queryForObject("select role from users where id = ?", String.class, user.id()))
                    .isEqualTo("OWNER");
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean assign(UUID userId, UUID restaurantId) {
        try {
            assignmentService.create(new AssignmentCommand(userId, restaurantId, "OWNER"));
            return true;
        } catch (AdminUserConflictException exception) {
            return false;
        }
    }

    private UUID insertRestaurant(String status, boolean deleted) {
        UUID id = UUID.randomUUID();
        restaurantIds.add(id);
        Instant now = Instant.parse("2026-10-14T03:00:00Z");
        jdbcTemplate.update("""
                insert into restaurants (
                    id, slug, name, city_slug, timezone, approval_status, deleted_at, created_at, updated_at
                ) values (?, ?, ?, 'ho-chi-minh', 'Asia/Ho_Chi_Minh', ?, ?, ?, ?)
                """,
                id, "assignment-" + id, "Restaurant " + shortId(id), status,
                deleted ? Timestamp.from(now) : null, Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
