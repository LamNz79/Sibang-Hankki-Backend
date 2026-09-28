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
    private Instant testStart;

    @BeforeEach
    void findPrototypeRestaurant() {
        restaurantId = jdbcTemplate.queryForObject(
                "select id from restaurants where slug = 'anan-saigon'", UUID.class);
        testStart = Instant.parse("2100-01-01T00:00:00Z")
                .plusSeconds(Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 3_155_760_000L));
    }

    @Test
    void persistsGuestCapacityValues() {
        BookingSlot slot = repository.saveAndFlush(slot(testStart, 20, 7));

        assertEquals(restaurantId, slot.getRestaurantId());
        assertEquals(20, slot.getCapacityTotal());
        assertEquals(7, slot.getCapacityReserved());
    }

    @Test
    void findsRestaurantSlotsInStartTimeOrder() {
        BookingSlot later = repository.saveAndFlush(slot(testStart.plusSeconds(3600), 20, 0));
        BookingSlot earlier = repository.saveAndFlush(slot(testStart, 20, 0));

        List<BookingSlot> slots = repository.findByRestaurantIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
                restaurantId, testStart.minusSeconds(3600), testStart.plusSeconds(7200));

        assertEquals(List.of(earlier.getStartsAt(), later.getStartsAt()), slots.stream().map(BookingSlot::getStartsAt).toList());
    }

    @Test
    void rejectsEndAtOrBeforeStartAt() {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(new BookingSlot(
                        restaurantId, testStart, testStart, 20, 0)));
    }

    @Test
    void rejectsNegativeTotalCapacity() {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(slot(testStart, -1, 0)));
    }

    @Test
    void rejectsNegativeReservedCapacity() {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(slot(testStart, 20, -1)));
    }

    @Test
    void rejectsDuplicateRestaurantAndStartTime() {
        repository.saveAndFlush(slot(testStart, 20, 0));

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(slot(testStart, 30, 0)));
    }

    @Test
    void enforcesRestaurantForeignKey() {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(new BookingSlot(
                        UUID.randomUUID(), testStart, testStart.plusSeconds(1800), 20, 0)));
    }

    private BookingSlot slot(Instant startsAt, int capacityTotal, int capacityReserved) {
        return new BookingSlot(restaurantId, startsAt, startsAt.plusSeconds(1800), capacityTotal, capacityReserved);
    }
}
