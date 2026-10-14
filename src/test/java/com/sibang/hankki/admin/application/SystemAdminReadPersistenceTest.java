package com.sibang.hankki.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
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
class SystemAdminReadPersistenceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-13T03:00:00Z");

    @Autowired
    private SystemAdminReadService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void usersAreFilteredPagedSortedAndSoftDeletedRowsStayHidden() {
        String marker = "sa" + UUID.randomUUID().toString().substring(0, 6);
        for (int index = 1; index <= 11; index++) {
            insertUser(
                    uuid("81", index),
                    marker + index,
                    marker + index + "@example.com",
                    "User " + index,
                    index == 1 ? "ADMIN" : "CUSTOMER",
                    index == 2 ? "SUSPENDED" : "ACTIVE",
                    null);
        }
        insertUser(uuid("81", 99), marker + "deleted", null, "Deleted", "CUSTOMER", "ACTIVE", CREATED_AT);

        var firstPage = service.findUsers(0, 10, marker, null, null);
        var secondPage = service.findUsers(1, 10, marker, null, null);
        var admins = service.findUsers(0, 10, marker, "ADMIN", null);
        var suspended = service.findUsers(0, 10, marker, null, "SUSPENDED");

        assertThat(firstPage.totalElements()).isEqualTo(11);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.items()).hasSize(10);
        assertThat(firstPage.items().get(0).id()).isEqualTo(uuid("81", 11));
        assertThat(secondPage.items()).extracting(item -> item.id()).containsExactly(uuid("81", 1));
        assertThat(admins.items()).extracting(item -> item.id()).containsExactly(uuid("81", 1));
        assertThat(suspended.items()).extracting(item -> item.id()).containsExactly(uuid("81", 2));
    }

    @Test
    void userSearchCoversUseridNameAndEmailCaseInsensitively() {
        String marker = "Find" + UUID.randomUUID().toString().substring(0, 6);
        insertUser(uuid("82", 1), marker.toLowerCase(), null, "First", "CUSTOMER", "ACTIVE", null);
        insertUser(uuid("82", 2), "plain-name-user", null, marker.toUpperCase(), "CUSTOMER", "ACTIVE", null);
        insertUser(uuid("82", 3), "plain-email-user", marker + "@example.com", "Third", "CUSTOMER", "ACTIVE", null);

        var result = service.findUsers(0, 20, marker.toLowerCase(), null, null);

        assertThat(result.items()).extracting(item -> item.id())
                .containsExactly(uuid("82", 3), uuid("82", 2), uuid("82", 1));
    }

    @Test
    void restaurantsIncludeAllStatusesAndApplyFiltersWithoutLeakingDeletedRows() {
        String marker = "system-admin-" + UUID.randomUUID().toString().substring(0, 6);
        List<String> statuses = List.of("DRAFT", "PENDING", "CHANGES_REQUESTED", "ACTIVE", "SUSPENDED");
        for (int index = 1; index <= statuses.size(); index++) {
            insertRestaurant(
                    uuid("83", index),
                    marker + "-" + index,
                    "Restaurant " + marker + " " + index,
                    index == 2 ? "da-nang" : "ho-chi-minh",
                    statuses.get(index - 1),
                    null);
        }
        insertRestaurant(
                uuid("83", 99), marker + "-deleted", "Deleted " + marker,
                "ho-chi-minh", "ACTIVE", CREATED_AT);
        jdbcTemplate.update(
                "update restaurants set province_code = 79, province_name = 'Thành phố Hồ Chí Minh' where id = ?",
                uuid("83", 1));

        var all = service.findRestaurants(0, 10, marker.toUpperCase(), null, null);
        var pending = service.findRestaurants(0, 10, marker, "PENDING", "da-nang");
        var province = service.findRestaurants(0, 10, marker, null, null, 79);

        assertThat(all.totalElements()).isEqualTo(5);
        assertThat(all.items()).extracting(item -> item.approvalStatus())
                .containsExactly("SUSPENDED", "ACTIVE", "CHANGES_REQUESTED", "PENDING", "DRAFT");
        assertThat(pending.items()).extracting(item -> item.id()).containsExactly(uuid("83", 2));
        assertThat(province.items()).extracting(item -> item.id()).containsExactly(uuid("83", 1));
        assertThat(province.items().get(0).provinceName()).isEqualTo("Thành phố Hồ Chí Minh");
        assertThat(all.items().stream().filter(item -> item.id().equals(uuid("83", 2))).findFirst().orElseThrow()
                .provinceCode()).isNull();
    }

    private void insertUser(
            UUID id,
            String userid,
            String email,
            String name,
            String role,
            String status,
            Instant deletedAt) {
        jdbcTemplate.update("""
                insert into users (
                    id, userid, email, password_hash, name, role, status, restaurant_id,
                    status_changed_at, deleted_at, created_at, updated_at
                ) values (?, ?, ?, 'hash', ?, ?, ?, null, ?, ?, ?, ?)
                """,
                id, userid, email, name, role, status,
                Timestamp.from(CREATED_AT), timestamp(deletedAt),
                Timestamp.from(CREATED_AT), Timestamp.from(CREATED_AT));
    }

    private void insertRestaurant(
            UUID id,
            String slug,
            String name,
            String citySlug,
            String status,
            Instant deletedAt) {
        jdbcTemplate.update("""
                insert into restaurants (
                    id, slug, name, city_slug, area, district, timezone, approval_status,
                    deleted_at, created_at, updated_at
                ) values (?, ?, ?, ?, 'Area', 'District', 'Asia/Ho_Chi_Minh', ?, ?, ?, ?)
                """,
                id, slug, name, citySlug, status, timestamp(deletedAt),
                Timestamp.from(CREATED_AT), Timestamp.from(CREATED_AT));
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private UUID uuid(String prefix, int suffix) {
        return UUID.fromString(prefix + "000000-0000-0000-0000-" + String.format("%012d", suffix));
    }
}
