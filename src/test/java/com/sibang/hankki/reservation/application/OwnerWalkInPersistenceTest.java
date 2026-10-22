package com.sibang.hankki.reservation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sibang.hankki.reservation.adapter.out.persistence.repository.ReservationEventJpaRepository;
import com.sibang.hankki.reservation.adapter.out.persistence.repository.ReservationJpaRepository;
import com.sibang.hankki.reservation.application.exception.InvalidReservationStateException;
import com.sibang.hankki.reservation.application.exception.NoActiveBookingSlotException;
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
        Fixture fixture = fixtureWithSlot(NOW.minusSeconds(1800), NOW.plusSeconds(3600));
        OwnerWalkInCommand command = new OwnerWalkInCommand(
                "walk-in-it-" + UUID.randomUUID(), fixture.restaurantId(), fixture.actorId(),
                "Nguyen Van A", "0901234567", 4, "Window");

        var created = service.createWalkIn(command);
        var replay = service.createWalkIn(command);

        assertFalse(created.idempotentReplay());
        assertTrue(replay.idempotentReplay());
        assertEquals(created.reservation().id(), replay.reservation().id());
        var persisted = reservationRepository.findById(created.reservation().id()).orElseThrow();
        assertEquals(fixture.restaurantId(), persisted.getRestaurantId());
        assertEquals(fixture.slotId(), persisted.getBookingSlotId());
        assertEquals(ReservationStatus.CONFIRMED, persisted.getStatus());
        assertEquals(VisitStatus.ARRIVED, persisted.getVisitStatus());
        assertEquals(NOW, persisted.getCheckedInAt());
        assertEquals(fixture.actorId(), persisted.getCheckedInBy());
        assertEquals(4, jdbcTemplate.queryForObject(
                "select capacity_reserved from booking_slots where id = ?", Integer.class, fixture.slotId()));
        assertEquals(
                List.of(
                        ReservationEventType.REQUESTED,
                        ReservationEventType.CONFIRMED,
                        ReservationEventType.CHECKED_IN),
                eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(persisted.getId()).stream()
                        .map(event -> event.getEventType())
                        .toList());
        assertEquals(1, reservationRepository
                .findAllByRestaurantIdOrderByStartsAtAscIdAsc(fixture.restaurantId()).size());
    }

    @Test
    void walkInCanProceedFromArrivedToSeatedAndCompletedWithoutSkippingOrMovingBackward() {
        Fixture fixture = fixtureWithSlot(NOW.minusSeconds(1800), NOW.plusSeconds(3600));
        var walkIn = service.createWalkIn(new OwnerWalkInCommand(
                "walk-in-lifecycle-" + UUID.randomUUID(), fixture.restaurantId(), fixture.actorId(),
                "Lifecycle Guest", "0901234567", 2, null)).reservation();

        assertEquals(VisitStatus.ARRIVED, walkIn.visitStatus());
        assertThrows(InvalidReservationStateException.class,
                () -> service.complete(walkIn.id(), fixture.restaurantId(), fixture.actorId()));

        var seated = service.seat(walkIn.id(), fixture.restaurantId(), fixture.actorId());
        var completed = service.complete(walkIn.id(), fixture.restaurantId(), fixture.actorId());

        assertEquals(VisitStatus.SEATED, seated.visitStatus());
        assertEquals(VisitStatus.COMPLETED, completed.visitStatus());
        assertThrows(InvalidReservationStateException.class,
                () -> service.seat(walkIn.id(), fixture.restaurantId(), fixture.actorId()));
        var eventTypes = eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(walkIn.id()).stream()
                .map(event -> event.getEventType())
                .toList();
        assertEquals(5, eventTypes.size());
        for (ReservationEventType eventType : List.of(
                ReservationEventType.REQUESTED,
                ReservationEventType.CONFIRMED,
                ReservationEventType.CHECKED_IN,
                ReservationEventType.SEATED,
                ReservationEventType.COMPLETED)) {
            assertEquals(1, eventTypes.stream().filter(eventType::equals).count());
        }
    }

    @Test
    void qrCheckInMovesConfirmedExpectedReservationToArrivedAtFixedTime() {
        Fixture fixture = fixtureWithSlot(NOW.plusSeconds(1800), NOW.plusSeconds(7200));
        UUID reservationId = UUID.randomUUID();
        String rawToken = "walk-in-regression-qr-token";
        jdbcTemplate.update("""
                insert into reservations (
                    id, reference, idempotency_key, request_fingerprint, restaurant_id,
                    booking_slot_id, customer_name, customer_phone, starts_at, ends_at,
                    party_size, status, visit_status, check_in_token_hash)
                select ?, ?, ?, ?, restaurant_id, id, 'QR Guest', '0901234567', starts_at, ends_at,
                       2, 'CONFIRMED', 'EXPECTED', ?
                from booking_slots where id = ?
                """, reservationId, "SHK-" + reservationId.toString().substring(0, 8),
                "qr-check-in-" + reservationId, "fingerprint-" + reservationId,
                ReservationToken.hash(rawToken), fixture.slotId());

        var arrived = service.checkIn(rawToken, fixture.restaurantId(), fixture.actorId());

        assertEquals(reservationId, arrived.id());
        assertEquals(VisitStatus.ARRIVED, arrived.visitStatus());
        assertEquals(NOW, arrived.checkedInAt());
        assertEquals(fixture.actorId(), arrived.checkedInBy());
        var events = eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(reservationId);
        assertEquals(1, events.size());
        assertEquals(ReservationEventType.CHECKED_IN, events.get(0).getEventType());
    }

    @Test
    void walkInOutsideCurrentServiceSlotReturnsConflictConditionWithoutCreatingData() {
        Fixture fixture = fixtureWithSlot(NOW.plusSeconds(1800), NOW.plusSeconds(7200));

        assertThrows(NoActiveBookingSlotException.class, () -> service.createWalkIn(new OwnerWalkInCommand(
                "outside-service-" + UUID.randomUUID(), fixture.restaurantId(), fixture.actorId(),
                "Early Guest", "0901234567", 2, null)));

        assertTrue(reservationRepository
                .findAllByRestaurantIdOrderByStartsAtAscIdAsc(fixture.restaurantId()).isEmpty());
        assertEquals(0, jdbcTemplate.queryForObject(
                "select capacity_reserved from booking_slots where id = ?", Integer.class, fixture.slotId()));
    }

    private Fixture fixtureWithSlot(Instant startsAt, Instant endsAt) {
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
                """, slotId, restaurantId, Timestamp.from(startsAt), Timestamp.from(endsAt));
        return new Fixture(restaurantId, actorId, slotId);
    }

    private record Fixture(UUID restaurantId, UUID actorId, UUID slotId) {
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
