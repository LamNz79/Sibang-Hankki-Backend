package com.sibang.hankki.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.sibang.hankki.reservation.application.OwnerReservationReadService;
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

@SpringBootTest(properties = "app.reporting.time-zone=Asia/Ho_Chi_Minh")
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class SystemAdminReservationReadPersistenceTest {

    @Autowired
    private SystemAdminReservationReadService service;

    @Autowired
    private OwnerReservationReadService ownerReadService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void readsAcrossRestaurantsAndFiltersByRestaurantWithoutChangingOwnerScoping() {
        String marker = marker();
        UUID firstRestaurant = insertRestaurant(marker + "-alpha", "Alpha " + marker);
        UUID secondRestaurant = insertRestaurant(marker + "-beta", "Beta " + marker);
        UUID firstReservation = uuid("91", 1);
        UUID secondReservation = uuid("91", 2);
        insertReservation(firstRestaurant, firstReservation, marker + "-REF-1", "Alice", "0901111111",
                "alice@example.com", Instant.parse("2026-10-05T11:30:00Z"), "PENDING", null);
        insertReservation(secondRestaurant, secondReservation, marker + "-REF-2", "Bob", "0902222222",
                "bob@example.com", Instant.parse("2026-10-06T11:30:00Z"), "CONFIRMED", "EXPECTED");

        var all = service.findPage(0, 20, marker, null, null, null, null);
        var firstOnly = service.findPage(0, 20, marker, firstRestaurant, null, null, null);

        assertThat(all.items()).extracting(item -> item.id())
                .containsExactly(secondReservation, firstReservation);
        assertThat(all.items()).extracting(item -> item.restaurantName())
                .containsExactly("Beta " + marker, "Alpha " + marker);
        assertThat(firstOnly.items()).extracting(item -> item.id()).containsExactly(firstReservation);
        assertThat(ownerReadService.findAll(firstRestaurant)).extracting(item -> item.id())
                .containsExactly(firstReservation);
    }

    @Test
    void queryCoversReservationCustomerAndRestaurantFieldsCaseInsensitively() {
        String marker = marker();
        UUID restaurant = insertRestaurant("slug-" + marker, "Restaurant " + marker);
        Instant startsAt = Instant.parse("2026-10-05T11:30:00Z");
        insertReservation(restaurant, uuid("92", 1), "REF-" + marker, "Alice", "0901111111",
                "alice@example.com", startsAt, "PENDING", null);
        insertReservation(restaurant, uuid("92", 2), "OTHER-1", "Customer " + marker, "0902222222",
                "bob@example.com", startsAt.plusSeconds(1), "PENDING", null);
        insertReservation(restaurant, uuid("92", 3), "OTHER-2", "Charlie", "PHONE-" + marker,
                "charlie@example.com", startsAt.plusSeconds(2), "PENDING", null);
        insertReservation(restaurant, uuid("92", 4), "OTHER-3", "Delta", "0904444444",
                marker + "@example.com", startsAt.plusSeconds(3), "PENDING", null);

        assertThat(service.findPage(0, 20, "REF-" + marker.toUpperCase(), null, null, null, null).totalElements())
                .isEqualTo(1);
        assertThat(service.findPage(0, 20, "CUSTOMER " + marker.toUpperCase(), null, null, null, null)
                .totalElements()).isEqualTo(1);
        assertThat(service.findPage(0, 20, "PHONE-" + marker.toUpperCase(), null, null, null, null)
                .totalElements()).isEqualTo(1);
        assertThat(service.findPage(0, 20, (marker + "@EXAMPLE.COM").toUpperCase(), null, null, null, null)
                .totalElements()).isEqualTo(1);
        assertThat(service.findPage(0, 20, ("RESTAURANT " + marker).toUpperCase(), null, null, null, null)
                .totalElements()).isEqualTo(4);
        assertThat(service.findPage(0, 20, ("SLUG-" + marker).toUpperCase(), null, null, null, null)
                .totalElements()).isEqualTo(4);
    }

    @Test
    void appliesAllStatusMappingsAndInclusiveReportingDates() {
        String marker = marker();
        UUID restaurant = insertRestaurant(marker, marker);
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        Instant startsAt = LocalDateTime.parse("2026-10-01T00:00:00").atZone(zone).toInstant();
        insertReservation(restaurant, uuid("93", 1), marker + "-PENDING", "A", "1", null,
                startsAt, "PENDING", null);
        insertReservation(restaurant, uuid("93", 2), marker + "-PROPOSED", "B", "2", null,
                startsAt.plusSeconds(1), "ALTERNATIVE_PROPOSED", null);
        insertReservation(restaurant, uuid("93", 3), marker + "-EXPECTED", "C", "3", null,
                startsAt.plusSeconds(2), "CONFIRMED", "EXPECTED");
        insertReservation(restaurant, uuid("93", 4), marker + "-ARRIVED", "D", "4", null,
                startsAt.plusSeconds(3), "CONFIRMED", "ARRIVED");
        insertReservation(restaurant, uuid("93", 5), marker + "-SEATED", "E", "5", null,
                startsAt.plusSeconds(4), "CONFIRMED", "SEATED");
        insertReservation(restaurant, uuid("93", 6), marker + "-COMPLETED", "F", "6", null,
                startsAt.plusSeconds(5), "CONFIRMED", "COMPLETED");
        insertReservation(restaurant, uuid("93", 7), marker + "-CANCELLED", "G", "7", null,
                startsAt.plusSeconds(6), "CANCELLED", null);
        insertReservation(restaurant, uuid("93", 8), marker + "-DECLINED", "H", "8", null,
                startsAt.plusSeconds(7), "DECLINED", null);
        insertReservation(restaurant, uuid("93", 9), marker + "-EXPIRED", "I", "9", null,
                startsAt.plusSeconds(8), "EXPIRED", null);
        insertReservation(restaurant, uuid("93", 10), marker + "-NO-SHOW", "J", "10", null,
                LocalDateTime.parse("2026-10-01T23:59:59").atZone(zone).toInstant(),
                "CONFIRMED", "NO_SHOW");
        insertReservation(restaurant, uuid("93", 11), marker + "-NEXT-DAY", "K", "11", null,
                LocalDateTime.parse("2026-10-02T00:00:00").atZone(zone).toInstant(),
                "PENDING", null);

        assertThat(pageForStatus(marker, "PENDING")).isEqualTo(3);
        assertThat(pageForStatus(marker, "CONFIRMED")).isEqualTo(1);
        assertThat(pageForStatus(marker, "CHECKED_IN")).isEqualTo(3);
        assertThat(pageForStatus(marker, "COMPLETED")).isEqualTo(1);
        assertThat(pageForStatus(marker, "CANCELLED")).isEqualTo(1);
        assertThat(pageForStatus(marker, "DECLINED")).isEqualTo(2);
        assertThat(pageForStatus(marker, "NO_SHOW")).isEqualTo(1);
        assertThat(service.findPage(0, 20, marker, null, null, "2026-10-01", "2026-10-01")
                .totalElements()).isEqualTo(10);
    }

    @Test
    void returnsStablePaginationMetadataAndAnEmptyPagePastTheEnd() {
        String marker = marker();
        UUID restaurant = insertRestaurant(marker, marker);
        Instant startsAt = Instant.parse("2026-10-05T11:30:00Z");
        for (int index = 1; index <= 11; index++) {
            insertReservation(restaurant, uuid("94", index), marker + "-" + index, "Tie " + marker,
                    Integer.toString(index), null, startsAt, "PENDING", null);
        }

        var first = service.findPage(0, 10, marker, null, null, null, null);
        var second = service.findPage(1, 10, marker, null, null, null, null);
        var empty = service.findPage(5, 10, marker, null, null, null, null);

        assertThat(first.totalElements()).isEqualTo(11);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.items()).hasSize(10);
        assertThat(first.items().get(0).id()).isEqualTo(uuid("94", 11));
        assertThat(second.items()).extracting(item -> item.id()).containsExactly(uuid("94", 1));
        assertThat(empty.items()).isEmpty();
        assertThat(empty.totalElements()).isEqualTo(11);
    }

    @Test
    void detailReturnsRestaurantAndReservationFields() {
        String marker = marker();
        UUID restaurant = insertRestaurant(marker, "Detail " + marker);
        UUID reservation = uuid("95", 1);
        UUID proposedSlot = UUID.randomUUID();
        Instant proposedStartsAt = Instant.parse("2026-10-06T11:30:00Z");
        insertReservation(restaurant, reservation, marker + "-DETAIL", "Detail Customer", "0900000000",
                "detail@example.com", Instant.parse("2026-10-05T11:30:00Z"), "PENDING", null);
        jdbcTemplate.update(
                "update reservations set special_request = 'Window seat', pre_order_note = 'No peanuts' where id = ?",
                reservation);
        jdbcTemplate.update("""
                insert into booking_slots (
                    id, restaurant_id, starts_at, ends_at, capacity_total, capacity_reserved
                ) values (?, ?, ?, ?, 4, 0)
                """, proposedSlot, restaurant, Timestamp.from(proposedStartsAt),
                Timestamp.from(proposedStartsAt.plusSeconds(5400)));
        jdbcTemplate.update("""
                insert into reservation_proposals (
                    id, reservation_id, proposed_booking_slot_id, proposed_starts_at,
                    proposed_party_size, reservation_version, message, status, initiator, created_by
                ) values (?, ?, ?, ?, 2, 0, 'Later please', 'PENDING', 'CUSTOMER', null)
                """, UUID.randomUUID(), reservation, proposedSlot, Timestamp.from(proposedStartsAt));

        var detail = service.findById(reservation);

        assertThat(detail.restaurantId()).isEqualTo(restaurant);
        assertThat(detail.restaurantName()).isEqualTo("Detail " + marker);
        assertThat(detail.specialRequest()).isEqualTo("Window seat");
        assertThat(detail.preOrderNote()).isEqualTo("No peanuts");
        assertThat(detail.alternativeProposal().initiator()).isEqualTo("CUSTOMER");
    }

    private long pageForStatus(String query, String status) {
        return service.findPage(0, 20, query, null, status, null, null).totalElements();
    }

    private UUID insertRestaurant(String slugPart, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into restaurants (id, slug, name, city_slug, timezone, approval_status)
                values (?, ?, ?, 'ho-chi-minh-city', 'Asia/Ho_Chi_Minh', 'ACTIVE')
                """, id, "admin-reservation-" + slugPart, name);
        return id;
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
                id, reference, "admin-reservation-" + compactId, compactId + compactId, restaurantId,
                customerName, customerEmail, customerPhone, Timestamp.from(startsAt),
                Timestamp.from(startsAt.plusSeconds(5400)), status, visitStatus);
    }

    private String marker() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UUID uuid(String prefix, int suffix) {
        return UUID.fromString(prefix + "000000-0000-0000-0000-" + String.format("%012d", suffix));
    }
}
