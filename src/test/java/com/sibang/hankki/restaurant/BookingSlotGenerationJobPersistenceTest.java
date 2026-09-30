package com.sibang.hankki.restaurant;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(BookingSlotGenerationPersistenceTest.FixedClockConfiguration.class)
@Transactional
@Rollback
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class BookingSlotGenerationJobPersistenceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);
    private static final LocalDate LAST_DAY = TODAY.plusDays(29);

    @Autowired
    private BookingSlotGenerationJob job;

    @Autowired
    private BookingSlotRepository slotRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private UUID restaurantId;

    @BeforeEach
    void preparePrototypeRestaurant() {
        restaurantId = jdbcTemplate.queryForObject(
                "select id from restaurants where slug = 'anan-saigon'", UUID.class);
        jdbcTemplate.update("delete from restaurant_booking_settings where restaurant_id = ?", restaurantId);
        executeV6();
        jdbcTemplate.update("delete from booking_slots where restaurant_id = ? and starts_at >= ? and starts_at < ?",
                restaurantId, timestamp(start()), timestamp(end()));
    }

    @Test
    void generatesPrototypeSlotsIdempotentlyWithoutOverwritingCapacity() {
        int firstRun = job.run();
        entityManager.flush();
        List<BookingSlot> generated = slotsInRange();

        assertTrue(firstRun >= generated.size());
        assertEquals(300, generated.size());
        assertEquals(TODAY, generated.get(0).getStartsAt()
                .atZone(BookingSlotGenerationService.RESTAURANT_TIME_ZONE).toLocalDate());
        assertEquals(LocalTime.of(11, 30), generated.get(0).getStartsAt()
                .atZone(BookingSlotGenerationService.RESTAURANT_TIME_ZONE).toLocalTime());

        Instant startsAt = generated.get(0).getStartsAt();
        assertEquals(1, jdbcTemplate.update("""
                update booking_slots
                set capacity_total = 99, capacity_reserved = 5
                where restaurant_id = ? and starts_at = ?
                """, restaurantId, timestamp(startsAt)));
        entityManager.clear();

        assertEquals(0, job.run());
        entityManager.flush();
        assertEquals(300, slotsInRange().size());
        assertEquals(99, jdbcTemplate.queryForObject(
                "select capacity_total from booking_slots where restaurant_id = ? and starts_at = ?",
                Integer.class, restaurantId, timestamp(startsAt)));
        assertEquals(5, jdbcTemplate.queryForObject(
                "select capacity_reserved from booking_slots where restaurant_id = ? and starts_at = ?",
                Integer.class, restaurantId, timestamp(startsAt)));
    }

    private List<BookingSlot> slotsInRange() {
        return slotRepository.findByRestaurantIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
                restaurantId, start(), end());
    }

    private Instant start() {
        return TODAY.atStartOfDay(BookingSlotGenerationService.RESTAURANT_TIME_ZONE).toInstant();
    }

    private Instant end() {
        return LAST_DAY.plusDays(1).atStartOfDay(BookingSlotGenerationService.RESTAURANT_TIME_ZONE).toInstant();
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
