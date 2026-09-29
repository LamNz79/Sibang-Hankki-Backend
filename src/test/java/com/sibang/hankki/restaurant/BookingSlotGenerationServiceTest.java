package com.sibang.hankki.restaurant;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BookingSlotGenerationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    private final UUID restaurantId = UUID.randomUUID();
    private RestaurantCatalogRepository restaurantRepository;
    private RestaurantBookingSettingsRepository settingsRepository;
    private BookingSlotRepository slotRepository;
    private BookingSlotGenerationService service;

    @BeforeEach
    void setUp() {
        restaurantRepository = mock(RestaurantCatalogRepository.class);
        settingsRepository = mock(RestaurantBookingSettingsRepository.class);
        slotRepository = mock(BookingSlotRepository.class);
        service = new BookingSlotGenerationService(
                restaurantRepository,
                settingsRepository,
                slotRepository,
                Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    @Test
    void generatesSlotsUsingBookingIntervalAndDiningDuration() {
        configure((short) 15, (short) 30, List.of(hours(1, "10:00", "11:00")));

        List<BookingSlot> slots = service.generateSlots(restaurantId, TODAY, TODAY);

        assertEquals(List.of(
                        Instant.parse("2026-09-28T03:00:00Z"),
                        Instant.parse("2026-09-28T03:15:00Z"),
                        Instant.parse("2026-09-28T03:30:00Z")),
                slots.stream().map(BookingSlot::getStartsAt).toList());
    }

    @Test
    void doesNotGenerateSlotWhoseDiningPeriodEndsAfterClosing() {
        configure((short) 15, (short) 30, List.of(hours(1, "10:00", "10:45")));

        List<BookingSlot> slots = service.generateSlots(restaurantId, TODAY, TODAY);

        assertEquals(List.of(
                        Instant.parse("2026-09-28T03:00:00Z"),
                        Instant.parse("2026-09-28T03:15:00Z")),
                slots.stream().map(BookingSlot::getStartsAt).toList());
    }

    @Test
    void supportsSplitLunchAndDinnerPeriods() {
        configure((short) 30, (short) 30, List.of(
                hours(1, "11:00", "12:00"),
                hours(1, "17:00", "18:00")));

        List<BookingSlot> slots = service.generateSlots(restaurantId, TODAY, TODAY);

        assertEquals(List.of(
                        Instant.parse("2026-09-28T04:00:00Z"),
                        Instant.parse("2026-09-28T04:30:00Z"),
                        Instant.parse("2026-09-28T10:00:00Z"),
                        Instant.parse("2026-09-28T10:30:00Z")),
                slots.stream().map(BookingSlot::getStartsAt).toList());
    }

    @Test
    void deduplicatesSlotsFromOverlappingBusinessHourPeriods() {
        configure((short) 30, (short) 30, List.of(
                hours(1, "10:00", "11:00"),
                hours(1, "10:30", "11:30")));

        List<BookingSlot> slots = service.generateSlots(restaurantId, TODAY, TODAY);

        assertEquals(List.of(
                        Instant.parse("2026-09-28T03:00:00Z"),
                        Instant.parse("2026-09-28T03:30:00Z"),
                        Instant.parse("2026-09-28T04:00:00Z")),
                slots.stream().map(BookingSlot::getStartsAt).toList());
    }

    @Test
    void returnsNoSlotsOnDayWithoutBusinessHours() {
        configure((short) 15, (short) 30, List.of(hours(2, "10:00", "11:00")));

        assertEquals(List.of(), service.generateSlots(restaurantId, TODAY, TODAY));
        verify(slotRepository, never()).saveAll(anyList());
    }

    @Test
    void convertsLocalBusinessHoursUsingHoChiMinhTimezone() {
        configure((short) 30, (short) 30, List.of(hours(1, "11:30", "12:00")));

        List<BookingSlot> slots = service.generateSlots(restaurantId, TODAY, TODAY);

        assertEquals(Instant.parse("2026-09-28T04:30:00Z"), slots.get(0).getStartsAt());
    }

    @Test
    void rejectsFromDateAfterToDate() {
        assertThrows(IllegalArgumentException.class,
                () -> service.generateSlots(restaurantId, TODAY.plusDays(1), TODAY));
    }

    @Test
    void rejectsDatesBeforeToday() {
        assertThrows(IllegalArgumentException.class,
                () -> service.generateSlots(restaurantId, TODAY.minusDays(1), TODAY));
    }

    @Test
    void acceptsLastDateInBookingWindow() {
        LocalDate lastAllowedDate = TODAY.plusDays(29);
        configure((short) 15, (short) 30, List.of(hours(lastAllowedDate.getDayOfWeek().getValue(), "10:00", "10:30")));

        assertEquals(1, service.generateSlots(restaurantId, lastAllowedDate, lastAllowedDate).size());
    }

    @Test
    void rejectsOneDayBeyondBookingWindow() {
        configure((short) 15, (short) 30, List.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.generateSlots(restaurantId, TODAY.plusDays(30), TODAY.plusDays(30)));
    }

    @Test
    void rejectsSingleDayRequestFarBeyondBookingWindow() {
        configure((short) 15, (short) 30, List.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.generateSlots(restaurantId, TODAY.plusDays(365), TODAY.plusDays(365)));
    }

    @Test
    void acceptsMultiDayRangeInsideBookingWindow() {
        configure((short) 15, (short) 30, List.of());

        assertEquals(List.of(), service.generateSlots(restaurantId, TODAY.plusDays(5), TODAY.plusDays(9)));
    }

    @Test
    void rejectsRequiredInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> service.generateSlots(null, TODAY, TODAY));
        assertThrows(IllegalArgumentException.class,
                () -> service.generateSlots(restaurantId, null, TODAY));
        assertThrows(IllegalArgumentException.class,
                () -> service.generateSlots(restaurantId, TODAY, null));
    }

    private void configure(short interval, short duration, List<RestaurantBusinessHourEntity> hours) {
        given(restaurantRepository.findActiveById(restaurantId)).willReturn(Optional.of(new RestaurantEntity()));
        given(settingsRepository.findById(restaurantId)).willReturn(Optional.of(new RestaurantBookingSettings(
                restaurantId, 20, interval, duration, (short) 10, 30, ConfirmationMode.AUTO, null,
                (short) 30, (short) 1, (short) 6, (short) 7, 120, 15)));
        given(restaurantRepository.findBusinessHoursByRestaurantIds(List.of(restaurantId))).willReturn(hours);
        given(slotRepository.findByRestaurantIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
                org.mockito.ArgumentMatchers.eq(restaurantId), any(), any())).willReturn(List.of());
    }

    private RestaurantBusinessHourEntity hours(int dayOfWeek, String opensAt, String closesAt) {
        RestaurantBusinessHourEntity hours = mock(RestaurantBusinessHourEntity.class);
        given(hours.getDayOfWeek()).willReturn((short) dayOfWeek);
        given(hours.getOpensAt()).willReturn(LocalTime.parse(opensAt));
        given(hours.getClosesAt()).willReturn(LocalTime.parse(closesAt));
        return hours;
    }
}
