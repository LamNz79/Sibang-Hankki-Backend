package com.sibang.hankki.restaurant.application.booking;

import com.sibang.hankki.restaurant.application.port.out.BookingSettings;
import com.sibang.hankki.restaurant.application.port.out.BookingSettingsPort;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantBusinessHourData;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantData;
import com.sibang.hankki.restaurant.domain.booking.BookingSlotCandidate;
import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BookingSlotGenerationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    private final UUID restaurantId = UUID.randomUUID();
    private RestaurantCatalogPort restaurantCatalogPort;
    private BookingSettingsPort bookingSettingsPort;
    private BookingSlotPort bookingSlotPort;
    private BookingSlotGenerationService service;

    @BeforeEach
    void setUp() {
        restaurantCatalogPort = mock(RestaurantCatalogPort.class);
        bookingSettingsPort = mock(BookingSettingsPort.class);
        bookingSlotPort = mock(BookingSlotPort.class);
        service = new BookingSlotGenerationService(
                restaurantCatalogPort,
                bookingSettingsPort,
                bookingSlotPort,
                Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    @Test
    void generatesSlotsUsingBookingIntervalAndDiningDuration() {
        configure(15, 30, List.of(hours(1, "10:00", "11:00")));

        List<BookingSlotCandidate> slots = service.generateSlots(restaurantId, TODAY, TODAY);

        assertEquals(List.of(
                        Instant.parse("2026-09-28T03:00:00Z"),
                        Instant.parse("2026-09-28T03:15:00Z"),
                        Instant.parse("2026-09-28T03:30:00Z")),
                slots.stream().map(BookingSlotCandidate::startsAt).toList());
    }

    @Test
    void doesNotGenerateSlotWhoseDiningPeriodEndsAfterClosing() {
        configure(15, 30, List.of(hours(1, "10:00", "10:45")));

        List<BookingSlotCandidate> slots = service.generateSlots(restaurantId, TODAY, TODAY);

        assertEquals(List.of(
                        Instant.parse("2026-09-28T03:00:00Z"),
                        Instant.parse("2026-09-28T03:15:00Z")),
                slots.stream().map(BookingSlotCandidate::startsAt).toList());
    }

    @Test
    void supportsSplitLunchAndDinnerPeriods() {
        configure(30, 30, List.of(hours(1, "11:00", "12:00"), hours(1, "17:00", "18:00")));

        List<BookingSlotCandidate> slots = service.generateSlots(restaurantId, TODAY, TODAY);

        assertEquals(List.of(
                        Instant.parse("2026-09-28T04:00:00Z"),
                        Instant.parse("2026-09-28T04:30:00Z"),
                        Instant.parse("2026-09-28T10:00:00Z"),
                        Instant.parse("2026-09-28T10:30:00Z")),
                slots.stream().map(BookingSlotCandidate::startsAt).toList());
    }

    @Test
    void deduplicatesSlotsFromOverlappingBusinessHourPeriods() {
        configure(30, 30, List.of(hours(1, "10:00", "11:00"), hours(1, "10:30", "11:30")));

        List<BookingSlotCandidate> slots = service.generateSlots(restaurantId, TODAY, TODAY);

        assertEquals(List.of(
                        Instant.parse("2026-09-28T03:00:00Z"),
                        Instant.parse("2026-09-28T03:30:00Z"),
                        Instant.parse("2026-09-28T04:00:00Z")),
                slots.stream().map(BookingSlotCandidate::startsAt).toList());
    }

    @Test
    void returnsNoSlotsOnDayWithoutBusinessHours() {
        configure(15, 30, List.of(hours(2, "10:00", "11:00")));

        assertEquals(List.of(), service.generateSlots(restaurantId, TODAY, TODAY));
        verify(bookingSlotPort, never()).saveGeneratedSlots(any(), anyList(), anyInt());
    }

    @Test
    void convertsLocalBusinessHoursUsingHoChiMinhTimezone() {
        configure(30, 30, List.of(hours(1, "11:30", "12:00")));

        List<BookingSlotCandidate> slots = service.generateSlots(restaurantId, TODAY, TODAY);

        assertEquals(Instant.parse("2026-09-28T04:30:00Z"), slots.get(0).startsAt());
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
        configure(15, 30, List.of(hours(lastAllowedDate.getDayOfWeek().getValue(), "10:00", "10:30")));

        assertEquals(1, service.generateSlots(restaurantId, lastAllowedDate, lastAllowedDate).size());
    }

    @Test
    void rejectsOneDayBeyondBookingWindow() {
        configure(15, 30, List.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.generateSlots(restaurantId, TODAY.plusDays(30), TODAY.plusDays(30)));
    }

    @Test
    void rejectsSingleDayRequestFarBeyondBookingWindow() {
        configure(15, 30, List.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.generateSlots(restaurantId, TODAY.plusDays(365), TODAY.plusDays(365)));
    }

    @Test
    void acceptsMultiDayRangeInsideBookingWindow() {
        configure(15, 30, List.of());

        assertEquals(List.of(), service.generateSlots(restaurantId, TODAY.plusDays(5), TODAY.plusDays(9)));
    }

    @Test
    void rejectsRequiredInputs() {
        assertThrows(IllegalArgumentException.class, () -> service.generateSlots(null, TODAY, TODAY));
        assertThrows(IllegalArgumentException.class, () -> service.generateSlots(restaurantId, null, TODAY));
        assertThrows(IllegalArgumentException.class, () -> service.generateSlots(restaurantId, TODAY, null));
    }

    private void configure(int interval, int duration, List<RestaurantBusinessHourData> hours) {
        given(restaurantCatalogPort.findActiveRestaurantById(restaurantId))
                .willReturn(Optional.of(restaurant()));
        given(bookingSettingsPort.findByRestaurantId(restaurantId)).willReturn(Optional.of(new BookingSettings(
                restaurantId, 20, interval, duration, ConfirmationMode.AUTO, null, 30, 1, 6, 7)));
        given(restaurantCatalogPort.findBusinessHoursByRestaurantIds(List.of(restaurantId))).willReturn(hours);
        given(bookingSlotPort.findSlotCapacities(org.mockito.ArgumentMatchers.eq(restaurantId), any(), any()))
                .willReturn(List.of());
    }

    private RestaurantBusinessHourData hours(int dayOfWeek, String opensAt, String closesAt) {
        return new RestaurantBusinessHourData(
                restaurantId, (short) dayOfWeek, LocalTime.parse(opensAt), LocalTime.parse(closesAt));
    }

    private RestaurantData restaurant() {
        return new RestaurantData(restaurantId, "anan-saigon", "", "", "", "", "", "", "", "", "");
    }
}
