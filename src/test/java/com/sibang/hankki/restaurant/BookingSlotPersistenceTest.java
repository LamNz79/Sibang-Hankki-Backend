package com.sibang.hankki.restaurant;

import java.time.Instant;
import java.util.List;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
@Rollback
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class BookingSlotPersistenceTest {

    @Autowired
    private BookingSlotRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID restaurantId;

    @BeforeEach
    void findPrototypeRestaurant() {
        restaurantId = jdbcTemplate.queryForObject(
                "select id from restaurants where slug = 'anan-saigon'", UUID.class);
    }

    @Test
    void persistsGuestCapacityValues() {
        BookingSlot slot = repository.saveAndFlush(slot("2026-10-01T10:00:00Z", 20, 7));

        assertEquals(restaurantId, slot.getRestaurantId());
        assertEquals(20, slot.getCapacityTotal());
        assertEquals(7, slot.getCapacityReserved());
    }

    @Test
    void findsRestaurantSlotsInStartTimeOrder() {
        BookingSlot later = repository.saveAndFlush(slot("2026-10-01T11:00:00Z", 20, 0));
        BookingSlot earlier = repository.saveAndFlush(slot("2026-10-01T10:00:00Z", 20, 0));

        List<BookingSlot> slots = repository.findByRestaurantIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
                restaurantId, Instant.parse("2026-10-01T09:00:00Z"), Instant.parse("2026-10-01T12:00:00Z"));

        assertEquals(List.of(earlier.getStartsAt(), later.getStartsAt()), slots.stream().map(BookingSlot::getStartsAt).toList());
    }

    @Test
    void rejectsEndAtOrBeforeStartAt() {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(new BookingSlot(
                        restaurantId, Instant.parse("2026-10-01T10:00:00Z"), Instant.parse("2026-10-01T10:00:00Z"), 20, 0)));
    }

    @Test
    void rejectsNegativeTotalCapacity() {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(slot("2026-10-01T10:00:00Z", -1, 0)));
    }

    @Test
    void rejectsNegativeReservedCapacity() {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(slot("2026-10-01T10:00:00Z", 20, -1)));
    }

    @Test
    void rejectsDuplicateRestaurantAndStartTime() {
        repository.saveAndFlush(slot("2026-10-01T10:00:00Z", 20, 0));

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(slot("2026-10-01T10:00:00Z", 30, 0)));
    }

    @Test
    void enforcesRestaurantForeignKey() {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(new BookingSlot(
                        UUID.randomUUID(), Instant.parse("2026-10-01T10:00:00Z"),
                        Instant.parse("2026-10-01T10:30:00Z"), 20, 0)));
    }

    private BookingSlot slot(String startsAt, int capacityTotal, int capacityReserved) {
        Instant start = Instant.parse(startsAt);
        return new BookingSlot(restaurantId, start, start.plusSeconds(1800), capacityTotal, capacityReserved);
    }
}
