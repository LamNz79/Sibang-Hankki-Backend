package com.sibang.hankki.reservation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.sibang.hankki.reservation.adapter.out.persistence.repository.ReservationEventJpaRepository;
import com.sibang.hankki.reservation.adapter.out.persistence.repository.ReservationJpaRepository;
import com.sibang.hankki.reservation.application.exception.InvalidReservationStateException;
import com.sibang.hankki.reservation.application.exception.ReservationCapacityUnavailableException;
import com.sibang.hankki.reservation.application.exception.ReservationNotFoundException;
import com.sibang.hankki.reservation.application.port.out.ReservationEventPersistencePort;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort;
import com.sibang.hankki.reservation.domain.model.Reservation;
import com.sibang.hankki.reservation.domain.model.ReservationEventType;
import com.sibang.hankki.reservation.domain.model.ReservationStatus;
import com.sibang.hankki.reservation.domain.model.VisitStatus;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.BookingSlotRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class OwnerReservationCommandPersistenceTest {

    private static final Instant STARTS_AT = Instant.parse("2026-10-10T11:30:00Z");
    private static final Instant ENDS_AT = Instant.parse("2026-10-10T13:00:00Z");

    @Autowired
    private OwnerReservationCommandService service;
    @Autowired
    private ReservationJpaRepository reservationRepository;
    @Autowired
    private ReservationEventJpaRepository eventRepository;
    @Autowired
    private BookingSlotRepository slotRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean
    private ReservationPersistencePort reservationPersistencePort;
    @MockitoSpyBean
    private ReservationEventPersistencePort eventPersistencePort;

    private UUID restaurantId;
    private UUID actorId;
    private UUID slotId;

    @BeforeEach
    void setUp() {
        restaurantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into restaurants (id, slug, name, city_slug, approval_status)
                values (?, ?, 'Owner Command Test', 'ho-chi-minh-city', 'ACTIVE')
                """, restaurantId, "owner-command-test-" + restaurantId);
        jdbcTemplate.update("""
                insert into users (id, userid, password_hash, name, role, status, restaurant_id)
                values (?, ?, 'unused-test-hash', 'Test Owner', 'OWNER', 'ACTIVE', ?)
                """, actorId, "owner-" + actorId.toString().substring(0, 20), restaurantId);
        jdbcTemplate.update("""
                insert into booking_slots
                    (id, restaurant_id, starts_at, ends_at, capacity_total, capacity_reserved)
                values (?, ?, ?, ?, 4, 0)
                """, slotId, restaurantId, Timestamp.from(STARTS_AT), Timestamp.from(ENDS_AT));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from reservation_events where reservation_id in (select id from reservations where restaurant_id = ?)", restaurantId);
        jdbcTemplate.update("delete from reservations where restaurant_id = ?", restaurantId);
        jdbcTemplate.update("delete from booking_slots where restaurant_id = ?", restaurantId);
        jdbcTemplate.update("delete from users where id = ?", actorId);
        jdbcTemplate.update("delete from restaurants where id = ?", restaurantId);
    }

    @Test
    void confirmReservesCapacityOnceAndSetsExpectedVisitStatus() {
        UUID reservationId = insertPendingReservation(2);

        var confirmed = service.confirm(reservationId, restaurantId, actorId);
        var replay = service.confirm(reservationId, restaurantId, actorId);

        assertEquals(ReservationStatus.CONFIRMED, confirmed.status());
        assertEquals(VisitStatus.EXPECTED, confirmed.visitStatus());
        assertEquals(confirmed.id(), replay.id());
        assertEquals(2, slotRepository.findById(slotId).orElseThrow().getCapacityReserved());
        assertEquals(1, eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(reservationId).size());
        assertEquals(actorId, eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(reservationId)
                .get(0).getActorUserId());
    }

    @Test
    void insufficientCapacityRollsBackReservationAndEvent() {
        UUID reservationId = insertPendingReservation(5);

        assertThrows(ReservationCapacityUnavailableException.class,
                () -> service.confirm(reservationId, restaurantId, actorId));

        assertEquals(ReservationStatus.PENDING,
                reservationRepository.findById(reservationId).orElseThrow().getStatus());
        assertEquals(0, slotRepository.findById(slotId).orElseThrow().getCapacityReserved());
        assertEquals(0, eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(reservationId).size());
    }

    @Test
    void declineDoesNotReserveCapacityAndReplayDoesNotDuplicateEvent() {
        UUID reservationId = insertPendingReservation(2);

        var declined = service.decline(reservationId, restaurantId, actorId, "Fully booked");
        service.decline(reservationId, restaurantId, actorId, "Fully booked");

        assertEquals(ReservationStatus.DECLINED, declined.status());
        assertEquals(0, slotRepository.findById(slotId).orElseThrow().getCapacityReserved());
        var events = eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(reservationId);
        assertEquals(1, events.size());
        assertEquals(actorId, events.get(0).getActorUserId());
        assertTrue(events.get(0).getMetadata().contains("Fully booked"));
    }

    @Test
    void crossRestaurantReservationIsNotExposed() {
        UUID reservationId = insertPendingReservation(2);

        assertThrows(ReservationNotFoundException.class,
                () -> service.confirm(reservationId, UUID.randomUUID(), actorId));
    }

    @Test
    void concurrentConfirmCannotOversellCapacity() throws Exception {
        jdbcTemplate.update("update booking_slots set capacity_total = 2 where id = ?", slotId);
        UUID firstReservationId = insertPendingReservation(2);
        UUID secondReservationId = insertPendingReservation(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = executor.invokeAll(List.of(
                    confirm(firstReservationId), confirm(secondReservationId)));
            assertEquals(1, results.stream().filter(this::succeeded).count());
        } finally {
            executor.shutdownNow();
        }

        assertEquals(2, slotRepository.findById(slotId).orElseThrow().getCapacityReserved());
        assertEquals(1, reservationRepository.findAllByRestaurantIdOrderByStartsAtAscIdAsc(restaurantId).stream()
                .filter(reservation -> reservation.getStatus() == ReservationStatus.CONFIRMED)
                .count());
    }

    @Test
    void seatAndCompletePersistEachTransitionAndEventOnce() {
        UUID reservationId = insertConfirmedReservation(VisitStatus.ARRIVED);

        var seated = service.seat(reservationId, restaurantId, actorId);
        var seatedReplay = service.seat(reservationId, restaurantId, actorId);
        var completed = service.complete(reservationId, restaurantId, actorId);
        var completedReplay = service.complete(reservationId, restaurantId, actorId);

        assertEquals(VisitStatus.SEATED, seated.visitStatus());
        assertEquals(seated.id(), seatedReplay.id());
        assertEquals(VisitStatus.COMPLETED, completed.visitStatus());
        assertEquals(completed.id(), completedReplay.id());
        assertEquals(VisitStatus.COMPLETED,
                reservationRepository.findById(reservationId).orElseThrow().getVisitStatus());
        var events = eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(reservationId);
        assertEquals(2, events.size());
        assertEquals(1, events.stream()
                .filter(event -> event.getEventType() == ReservationEventType.SEATED).count());
        assertEquals(1, events.stream()
                .filter(event -> event.getEventType() == ReservationEventType.COMPLETED).count());
        assertTrue(events.stream().allMatch(event -> actorId.equals(event.getActorUserId())));
    }

    @Test
    void visitTransitionsRejectSkippingAndMovingBackward() {
        UUID expectedId = insertConfirmedReservation(VisitStatus.EXPECTED);
        UUID completedId = insertConfirmedReservation(VisitStatus.COMPLETED);
        UUID arrivedId = insertConfirmedReservation(VisitStatus.ARRIVED);

        assertThrows(InvalidReservationStateException.class,
                () -> service.seat(expectedId, restaurantId, actorId));
        assertThrows(InvalidReservationStateException.class,
                () -> service.seat(completedId, restaurantId, actorId));
        assertThrows(InvalidReservationStateException.class,
                () -> service.complete(arrivedId, restaurantId, actorId));
        assertEquals(0, eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(expectedId).size());
        assertEquals(0, eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(completedId).size());
        assertEquals(0, eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(arrivedId).size());
    }

    @Test
    void visitTransitionsDoNotExposeAnotherRestaurantReservation() {
        UUID reservationId = insertConfirmedReservation(VisitStatus.ARRIVED);
        UUID otherRestaurantId = UUID.randomUUID();

        assertThrows(ReservationNotFoundException.class,
                () -> service.seat(reservationId, otherRestaurantId, actorId));
        assertThrows(ReservationNotFoundException.class,
                () -> service.complete(reservationId, otherRestaurantId, actorId));
    }

    @Test
    void manualCheckInPersistsAuditOnceAndUsesRestaurantScope() {
        UUID reservationId = insertConfirmedReservation(VisitStatus.EXPECTED);

        var arrived = service.checkIn(reservationId, restaurantId, actorId);
        var replay = service.checkIn(reservationId, restaurantId, actorId);

        assertEquals(VisitStatus.ARRIVED, arrived.visitStatus());
        assertEquals(actorId, arrived.checkedInBy());
        assertTrue(arrived.checkedInAt() != null);
        assertEquals(arrived.id(), replay.id());
        var events = eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(reservationId);
        assertEquals(1, events.size());
        assertEquals(ReservationEventType.CHECKED_IN, events.get(0).getEventType());
        assertEquals(actorId, events.get(0).getActorUserId());
        assertThrows(ReservationNotFoundException.class,
                () -> service.checkIn(reservationId, UUID.randomUUID(), actorId));
    }

    @Test
    void manualCheckInRejectsInvalidTransition() {
        UUID pendingId = insertPendingReservation(2);
        UUID seatedId = insertConfirmedReservation(VisitStatus.SEATED);

        assertThrows(InvalidReservationStateException.class,
                () -> service.checkIn(pendingId, restaurantId, actorId));
        assertThrows(InvalidReservationStateException.class,
                () -> service.checkIn(seatedId, restaurantId, actorId));
        assertEquals(0, eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(pendingId).size());
        assertEquals(0, eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(seatedId).size());
    }

    @Test
    void ownerCancellationReleasesCapacityAndPersistsAuditOnce() {
        UUID reservationId = insertConfirmedReservation(VisitStatus.EXPECTED);
        jdbcTemplate.update("update booking_slots set capacity_reserved = 2 where id = ?", slotId);

        var cancelled = service.cancel(
                reservationId, restaurantId, actorId, "  Restaurant closed unexpectedly  ");
        var replay = service.cancel(reservationId, restaurantId, actorId, "Restaurant closed unexpectedly");

        assertEquals(ReservationStatus.CANCELLED, cancelled.status());
        assertEquals(cancelled.id(), replay.id());
        assertEquals(0, slotRepository.findById(slotId).orElseThrow().getCapacityReserved());
        var events = eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(reservationId);
        assertEquals(1, events.size());
        assertEquals(ReservationEventType.CANCELLED_BY_RESTAURANT, events.get(0).getEventType());
        assertEquals(actorId, events.get(0).getActorUserId());
        assertTrue(events.get(0).getMetadata().contains("Restaurant closed unexpectedly"));
    }

    @Test
    void ownerCancellationRejectsPendingAndPostArrivalReservations() {
        UUID pendingId = insertPendingReservation(2);
        UUID arrivedId = insertConfirmedReservation(VisitStatus.ARRIVED);
        UUID seatedId = insertConfirmedReservation(VisitStatus.SEATED);
        UUID completedId = insertConfirmedReservation(VisitStatus.COMPLETED);

        for (UUID reservationId : List.of(pendingId, arrivedId, seatedId, completedId)) {
            assertThrows(InvalidReservationStateException.class,
                    () -> service.cancel(reservationId, restaurantId, actorId, "Closed"));
            assertEquals(0, eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(reservationId).size());
        }
    }

    @Test
    void ownerCancellationReleaseFailureChangesNothing() {
        UUID reservationId = insertConfirmedReservation(VisitStatus.EXPECTED);
        jdbcTemplate.update("update booking_slots set capacity_reserved = 1 where id = ?", slotId);

        assertThrows(InvalidReservationStateException.class,
                () -> service.cancel(reservationId, restaurantId, actorId, "Closed"));

        assertEquals(1, slotRepository.findById(slotId).orElseThrow().getCapacityReserved());
        assertEquals(ReservationStatus.CONFIRMED,
                reservationRepository.findById(reservationId).orElseThrow().getStatus());
        assertEquals(0, eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(reservationId).size());
    }

    @Test
    void ownerCancellationSaveFailureRollsBackCapacityRelease() {
        UUID reservationId = insertConfirmedReservation(VisitStatus.EXPECTED);
        jdbcTemplate.update("update booking_slots set capacity_reserved = 2 where id = ?", slotId);
        doThrow(new IllegalStateException("reservation save failure"))
                .when(reservationPersistencePort).save(any(Reservation.class));

        assertThrows(IllegalStateException.class,
                () -> service.cancel(reservationId, restaurantId, actorId, "Closed"));

        assertCancellationRolledBack(reservationId);
    }

    @Test
    void ownerCancellationEventFailureRollsBackReservationAndCapacity() {
        UUID reservationId = insertConfirmedReservation(VisitStatus.EXPECTED);
        jdbcTemplate.update("update booking_slots set capacity_reserved = 2 where id = ?", slotId);
        doThrow(new IllegalStateException("event persistence failure"))
                .when(eventPersistencePort).append(any());

        assertThrows(IllegalStateException.class,
                () -> service.cancel(reservationId, restaurantId, actorId, "Closed"));

        assertCancellationRolledBack(reservationId);
    }

    private void assertCancellationRolledBack(UUID reservationId) {
        assertEquals(2, slotRepository.findById(slotId).orElseThrow().getCapacityReserved());
        assertEquals(ReservationStatus.CONFIRMED,
                reservationRepository.findById(reservationId).orElseThrow().getStatus());
        assertEquals(VisitStatus.EXPECTED,
                reservationRepository.findById(reservationId).orElseThrow().getVisitStatus());
        assertEquals(0, eventRepository.findByReservationIdOrderByCreatedAtAscIdAsc(reservationId).size());
    }

    private Callable<Boolean> confirm(UUID reservationId) {
        return () -> {
            try {
                service.confirm(reservationId, restaurantId, actorId);
                return true;
            } catch (ReservationCapacityUnavailableException exception) {
                return false;
            }
        };
    }

    private boolean succeeded(Future<Boolean> result) {
        try {
            return result.get();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private UUID insertPendingReservation(int partySize) {
        UUID reservationId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into reservations (
                    id, reference, idempotency_key, request_fingerprint, restaurant_id, booking_slot_id,
                    customer_name, customer_phone, starts_at, ends_at, party_size, status)
                values (?, ?, ?, ?, ?, ?, 'Minh Lam', '0900000000', ?, ?, ?, 'PENDING')
                """, reservationId, "SHK-" + reservationId.toString().substring(0, 8),
                "owner-command-" + reservationId, "0".repeat(64), restaurantId, slotId,
                Timestamp.from(STARTS_AT), Timestamp.from(ENDS_AT), partySize);
        return reservationId;
    }

    private UUID insertConfirmedReservation(VisitStatus visitStatus) {
        UUID reservationId = insertPendingReservation(2);
        jdbcTemplate.update(
                "update reservations set status = 'CONFIRMED', visit_status = ? where id = ?",
                visitStatus.name(), reservationId);
        return reservationId;
    }
}
