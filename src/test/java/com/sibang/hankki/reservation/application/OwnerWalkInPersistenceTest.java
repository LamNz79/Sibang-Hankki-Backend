package com.sibang.hankki.reservation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sibang.hankki.reservation.adapter.out.persistence.repository.ReservationEventJpaRepository;
import com.sibang.hankki.reservation.adapter.out.persistence.repository.ReservationJpaRepository;
import com.sibang.hankki.reservation.application.port.in.OwnerReservationCommandUseCase.OwnerWalkInCommand;
import com.sibang.hankki.reservation.domain.model.ReservationEventType;
import com.sibang.hankki.reservation.domain.model.ReservationStatus;
import com.sibang.hankki.reservation.domain.model.VisitStatus;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@Import(OwnerWalkInPersistenceTest.FixedClockConfiguration.class)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class OwnerWalkInPersistenceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T05:00:00Z");

    @Autowired
    private OwnerReservationCommandService service;

    @Autowired
    private ReservationJpaRepository reservationRepository;

    @Autowired
    private ReservationEventJpaRepository eventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsCheckedInWalkInAndReservesCapacityOnlyOnce() {
        UUID restaurantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into restaurants (id, slug, name, city_slug, approval_status)
                values (?, ?, 'Walk-in Test', 'ho-chi-minh-city', 'ACTIVE')
                """, restaurantId, "walk-in-test-" + restaurantId);
        jdbcTemplate.update("""
                insert into users (id, userid, password_hash, name, role, status, restaurant_id)
                values (?, ?, 'unused-test-hash', 'Walk-in Owner', 'OWNER', 'ACTIVE', ?)
                """, actorId, "walk-in-" + actorId.toString().substring(0, 20), restaurantId);
        jdbcTemplate.update("""
                insert into booking_slots
                    (id, restaurant_id, starts_at, ends_at, capacity_total, capacity_reserved)
                values (?, ?, ?, ?, 6, 0)
                """, slotId, restaurantId, Timestamp.from(NOW.minusSeconds(1800)),
                Timestamp.from(NOW.plusSeconds(3600)));
        OwnerWalkInCommand command = new OwnerWalkInCommand(
                "walk-in-it-" + UUID.randomUUID(), restaurantId, actorId,
                "Nguyen Van A", "0901234567", 4, "Window");

        var created = service.createWalkIn(command);
        var replay = service.createWalkIn(command);

        assertFalse(created.idempotentReplay());
        assertTrue(replay.idempotentReplay());
        assertEquals(created.reservation().id(), replay.reservation().id());
        var persisted = reservationRepository.findById(created.reservation().id()).orElseThrow();
        assertEquals(restaurantId, persisted.getRestaurantId());
        assertEquals(slotId, persisted.getBookingSlotId());
        assertEquals(ReservationStatus.CONFIRMED, persisted.getStatus());
        assertEquals(VisitStatus.ARRIVED, persisted.getVisitStatus());
        assertEquals(NOW, persisted.getCheckedInAt());
        assertEquals(actorId, persisted.getCheckedInBy());
        assertEquals(4, jdbcTemplate.queryForObject(
                "select capacity_reserved from booking_slots where id = ?", Integer.class, slotId));
        assertEquals(
                List.of(
                        ReservationEventType.REQUESTED,
                        ReservationEventType.CONFIRMED,
                        ReservationEventType.CHECKED_IN),
                eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(persisted.getId()).stream()
                        .map(event -> event.getEventType())
                        .toList());
        assertEquals(1, reservationRepository.findAllByRestaurantIdOrderByStartsAtAscIdAsc(restaurantId).size());
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock walkInTestClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
