package com.sibang.hankki.reservation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sibang.hankki.reservation.application.port.in.OwnerReservationReadUseCase.OwnerReservationPage;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
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
class OwnerReservationPaginationPersistenceTest {

    @Autowired
    private OwnerReservationReadService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void searchesEverySupportedFieldWithoutExposingAnotherRestaurant() {
        UUID restaurantId = insertRestaurant("Asia/Ho_Chi_Minh");
        UUID otherRestaurantId = insertRestaurant("Asia/Ho_Chi_Minh");
        Instant startsAt = Instant.parse("2026-10-10T11:30:00Z");
        insertReservation(restaurantId, UUID.randomUUID(), "REF-ALPHA", "Alice", "0901111111",
                "alice@example.com", startsAt, "PENDING", null);
        insertReservation(restaurantId, UUID.randomUUID(), "REF-BRAVO", "Bob Search", "0902222222",
                "bob@example.com", startsAt.plusSeconds(3600), "PENDING", null);
        insertReservation(restaurantId, UUID.randomUUID(), "REF-CHARLIE", "Charlie", "0903333333",
                "charlie-search@example.com", startsAt.plusSeconds(7200), "PENDING", null);
        insertReservation(otherRestaurantId, UUID.randomUUID(), "REF-ALPHA-OTHER", "Alice", "0901111111",
                "alice@example.com", startsAt, "PENDING", null);

        assertReferences(service.findPage(restaurantId, 0, 20, " alpha ", null, null, null), "REF-ALPHA");
        assertReferences(service.findPage(restaurantId, 0, 20, "BOB SEARCH", null, null, null), "REF-BRAVO");
        assertReferences(service.findPage(restaurantId, 0, 20, "090333", null, null, null), "REF-CHARLIE");
        assertReferences(service.findPage(restaurantId, 0, 20, "CHARLIE-SEARCH@EXAMPLE.COM", null, null, null),
                "REF-CHARLIE");
    }

    @Test
    void appliesEveryAggregateStatusAndKeepsSummaryIndependentFromFilters() {
        UUID restaurantId = insertRestaurant("Asia/Ho_Chi_Minh");
        Instant startsAt = Instant.parse("2026-10-10T11:30:00Z");
        insertReservation(restaurantId, UUID.randomUUID(), "PENDING-1", "Pending", "1", null,
                startsAt, "PENDING", null);
        insertReservation(restaurantId, UUID.randomUUID(), "PENDING-2", "Proposed", "2", null,
                startsAt, "ALTERNATIVE_PROPOSED", null);
        insertReservation(restaurantId, UUID.randomUUID(), "CONFIRMED-1", "Expected", "3", null,
                startsAt, "CONFIRMED", "EXPECTED");
        insertReservation(restaurantId, UUID.randomUUID(), "CHECKED-1", "Arrived", "4", null,
                startsAt, "CONFIRMED", "ARRIVED");
        insertReservation(restaurantId, UUID.randomUUID(), "CHECKED-2", "Seated", "5", null,
                startsAt, "CONFIRMED", "SEATED");
        insertReservation(restaurantId, UUID.randomUUID(), "CHECKED-3", "Completed", "6", null,
                startsAt, "CONFIRMED", "COMPLETED");
        insertReservation(restaurantId, UUID.randomUUID(), "CANCELLED-1", "Cancelled", "7", null,
                startsAt, "CANCELLED", null);
        insertReservation(restaurantId, UUID.randomUUID(), "DECLINED-1", "Declined", "8", null,
                startsAt, "DECLINED", null);
        insertReservation(restaurantId, UUID.randomUUID(), "DECLINED-2", "Expired", "9", null,
                startsAt, "EXPIRED", null);
        insertReservation(restaurantId, UUID.randomUUID(), "NO-SHOW-1", "No Show", "10", null,
                startsAt, "CONFIRMED", "NO_SHOW");

        assertEquals(2, pageForStatus(restaurantId, "PENDING").totalElements());
        OwnerReservationPage confirmed = pageForStatus(restaurantId, "CONFIRMED");
        assertEquals(1, confirmed.totalElements());
        assertEquals(3, pageForStatus(restaurantId, "CHECKED_IN").totalElements());
        assertEquals(1, pageForStatus(restaurantId, "CANCELLED").totalElements());
        assertEquals(2, pageForStatus(restaurantId, "DECLINED").totalElements());
        assertEquals(1, pageForStatus(restaurantId, "NO_SHOW").totalElements());

        assertEquals(1, confirmed.summary().confirmed());
        assertEquals(3, confirmed.summary().checkedIn());
        assertEquals(1, confirmed.summary().cancelled());
        assertEquals(1, confirmed.summary().noShow());
    }

    @Test
    void paginatesDeterministicallyAndReturnsEmptyPagePastTheEnd() {
        UUID restaurantId = insertRestaurant("Asia/Ho_Chi_Minh");
        Instant startsAt = Instant.parse("2026-10-10T11:30:00Z");
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000102");
        UUID third = UUID.fromString("00000000-0000-0000-0000-000000000103");
        insertReservation(restaurantId, third, "TIE-3", "Tie Group", "3", null, startsAt, "PENDING", null);
        insertReservation(restaurantId, first, "TIE-1", "Tie Group", "1", null, startsAt, "PENDING", null);
        insertReservation(restaurantId, second, "TIE-2", "Tie Group", "2", null, startsAt, "PENDING", null);

        OwnerReservationPage firstPage = service.findPage(
                restaurantId, 0, 2, "Tie Group", null, null, null);
        OwnerReservationPage secondPage = service.findPage(
                restaurantId, 1, 2, "Tie Group", null, null, null);
        OwnerReservationPage emptyPage = service.findPage(
                restaurantId, 5, 2, "Tie Group", null, null, null);

        assertEquals(List.of(first, second), firstPage.items().stream().map(r -> r.id()).toList());
        assertEquals(List.of(third), secondPage.items().stream().map(r -> r.id()).toList());
        assertEquals(3, emptyPage.totalElements());
        assertEquals(2, emptyPage.totalPages());
        assertTrue(emptyPage.items().isEmpty());
    }

    @Test
    void includesTheWholeDateToInTheRestaurantTimezone() {
        ZoneId zoneId = ZoneId.of("America/New_York");
        UUID restaurantId = insertRestaurant(zoneId.getId());
        Instant startOfDay = LocalDateTime.parse("2026-10-01T00:00:00").atZone(zoneId).toInstant();
        Instant endOfDay = LocalDateTime.parse("2026-10-01T23:59:59").atZone(zoneId).toInstant();
        Instant nextDay = LocalDateTime.parse("2026-10-02T00:00:00").atZone(zoneId).toInstant();
        insertReservation(restaurantId, UUID.randomUUID(), "DATE-START", "Date Filter", "1", null,
                startOfDay, "PENDING", null);
        insertReservation(restaurantId, UUID.randomUUID(), "DATE-END", "Date Filter", "2", null,
                endOfDay, "PENDING", null);
        insertReservation(restaurantId, UUID.randomUUID(), "DATE-NEXT", "Date Filter", "3", null,
                nextDay, "PENDING", null);

        OwnerReservationPage page = service.findPage(
                restaurantId, 0, 20, "Date Filter", null, "2026-10-01", "2026-10-01");

        assertEquals(2, page.totalElements());
        assertEquals(List.of("DATE-END", "DATE-START"),
                page.items().stream().map(r -> r.reference()).toList());
    }

    private OwnerReservationPage pageForStatus(UUID restaurantId, String status) {
        return service.findPage(restaurantId, 0, 20, null, status, null, null);
    }

    private void assertReferences(OwnerReservationPage page, String... references) {
        assertEquals(List.of(references), page.items().stream().map(r -> r.reference()).toList());
    }

    private UUID insertRestaurant(String timezone) {
        UUID restaurantId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into restaurants (id, slug, name, city_slug, timezone, approval_status)
                values (?, ?, 'Pagination Test', 'ho-chi-minh-city', ?, 'ACTIVE')
                """, restaurantId, "pagination-test-" + restaurantId, timezone);
        return restaurantId;
    }

    private void insertReservation(
            UUID restaurantId,
            UUID id,
            String reference,
            String customerName,
            String customerPhone,
            String customerEmail,
            Instant startsAt,
            String status,
            String visitStatus) {
        String compactId = id.toString().replace("-", "");
        jdbcTemplate.update("""
                insert into reservations (
                    id, reference, idempotency_key, request_fingerprint, restaurant_id,
                    customer_name, customer_email, customer_phone, starts_at, ends_at,
                    party_size, status, visit_status
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 2, ?, ?)
                """,
                id,
                reference,
                "pagination-" + compactId,
                compactId + compactId,
                restaurantId,
                customerName,
                customerEmail,
                customerPhone,
                Timestamp.from(startsAt),
                Timestamp.from(startsAt.plusSeconds(5400)),
                status,
                visitStatus);
    }
}
