package com.sibang.hankki.reservation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sibang.hankki.reservation.application.port.in.CreateReservationCommand;
import com.sibang.hankki.reservation.application.port.out.ReservationEventPersistencePort;
import com.sibang.hankki.reservation.domain.model.ReservationEvent;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.BookingSlot;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantBookingSettings;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.BookingSlotRepository;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantBookingSettingsRepository;
import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import({
        CreateReservationTransactionRollbackPersistenceTest.FixedClockConfiguration.class,
        CreateReservationTransactionRollbackPersistenceTest.FailingEventConfiguration.class
})
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class CreateReservationTransactionRollbackPersistenceTest {

    @Autowired
    private CreateReservationService service;
    @Autowired
    private BookingSlotRepository slotRepository;
    @Autowired
    private RestaurantBookingSettingsRepository settingsRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID restaurantId;
    private UUID slotId;

    @BeforeEach
    void setUp() {
        restaurantId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into restaurants (id, slug, name, city_slug, approval_status)
                values (?, ?, ?, ?, 'ACTIVE')
                """, restaurantId, slug(), "Reservation Rollback Test", "ho-chi-minh-city");
        settingsRepository.saveAndFlush(new RestaurantBookingSettings(
                restaurantId, 20, (short) 60, (short) 90, (short) 10, 30, ConfirmationMode.AUTO, null,
                (short) 30, (short) 1, (short) 10, (short) 11, 120, 15));
        Instant startsAt = LocalDate.of(2026, 10, 5).atTime(18, 30)
                .atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        slotId = slotRepository.saveAndFlush(new BookingSlot(
                restaurantId, startsAt, startsAt.plusSeconds(5400), 4, 0)).getId();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from reservation_events where reservation_id in (select id from reservations where restaurant_id = ?)", restaurantId);
        jdbcTemplate.update("delete from reservations where restaurant_id = ?", restaurantId);
        jdbcTemplate.update("delete from booking_slots where restaurant_id = ?", restaurantId);
        jdbcTemplate.update("delete from restaurant_booking_settings where restaurant_id = ?", restaurantId);
        jdbcTemplate.update("delete from restaurants where id = ?", restaurantId);
    }

    @Test
    void eventFailureRollsBackReservationAndCapacity() {
        assertThrows(IllegalStateException.class, () -> service.create(new CreateReservationCommand(
                "rollback", slug(), "2026-10-05", "18:30", 2, "Minh Lam", "0900000000", null, null, null)));

        assertEquals(0, slotRepository.findById(slotId).orElseThrow().getCapacityReserved());
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from reservations where restaurant_id = ?", Integer.class, restaurantId));
    }

    private String slug() {
        return "reservation-rollback-" + restaurantId;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-01T03:00:00Z"), ZoneOffset.UTC);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingEventConfiguration {

        @Bean
        @Primary
        ReservationEventPersistencePort failingReservationEventPersistencePort() {
            return new ReservationEventPersistencePort() {
                @Override
                public ReservationEvent append(ReservationEvent event) {
                    throw new IllegalStateException("event persistence failure");
                }

                @Override
                public List<ReservationEvent> findByReservationId(UUID reservationId) {
                    return List.of();
                }
            };
        }
    }
}
