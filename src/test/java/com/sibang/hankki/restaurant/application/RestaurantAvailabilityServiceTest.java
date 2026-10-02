package com.sibang.hankki.restaurant.application;

import com.sibang.hankki.restaurant.application.exception.BookingSettingsNotConfiguredException;
import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;
import com.sibang.hankki.restaurant.application.model.RestaurantAvailabilityResponse;
import com.sibang.hankki.restaurant.application.port.out.BookingSettings;
import com.sibang.hankki.restaurant.application.port.out.BookingSettingsPort;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantData;
import com.sibang.hankki.restaurant.domain.booking.BookingSlotCapacity;
import com.sibang.hankki.restaurant.domain.booking.BookingTime;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RestaurantAvailabilityServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    private final UUID restaurantId = UUID.randomUUID();
    private RestaurantCatalogPort restaurantCatalogPort;
    private BookingSettingsPort bookingSettingsPort;
    private BookingSlotPort bookingSlotPort;
    private RestaurantAvailabilityService service;

    @BeforeEach
    void setUp() {
        restaurantCatalogPort = mock(RestaurantCatalogPort.class);
        bookingSettingsPort = mock(BookingSettingsPort.class);
        bookingSlotPort = mock(BookingSlotPort.class);
        given(restaurantCatalogPort.findActiveRestaurantBySlug("anan-saigon"))
                .willReturn(Optional.of(restaurant("anan-saigon")));
        given(bookingSettingsPort.findByRestaurantId(restaurantId))
                .willReturn(Optional.of(settings(ConfirmationMode.AUTO, null, 1, 6, 7)));
        givenSlots();
        service = new RestaurantAvailabilityService(
                restaurantCatalogPort,
                bookingSettingsPort,
                bookingSlotPort,
                Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    @Test
    void returnsOrderedLocalTimesForSlotsWithEnoughCapacity() {
        givenSlots(slot("11:30", 2, 0), slot("12:30", 4, 2));

        RestaurantAvailabilityResponse response = service.availability("anan-saigon", TODAY.toString(), 2);

        assertEquals(List.of("11:30", "12:30"), response.slots());
        assertEquals(false, response.requiresRestaurantConfirmation());
    }

    @Test
    void excludesInsufficientAndFullyReservedSlots() {
        givenSlots(slot("11:30", 4, 3), slot("12:30", 4, 4), slot("13:30", 4, 2));

        assertEquals(List.of("13:30"), service.availability("anan-saigon", TODAY.toString(), 2).slots());
    }

    @Test
    void excludesPastAndCurrentSlotsForToday() {
        givenSlots(slot("11:30", 4, 0), slot("12:30", 4, 0), slot("13:30", 4, 0));
        service = new RestaurantAvailabilityService(
                restaurantCatalogPort,
                bookingSettingsPort,
                bookingSlotPort,
                Clock.fixed(
                        TODAY.atTime(12, 30).atZone(BookingTime.RESTAURANT_TIME_ZONE).toInstant(),
                        BookingTime.RESTAURANT_TIME_ZONE));

        assertEquals(List.of("13:30"), service.availability("anan-saigon", TODAY.toString(), 2).slots());
    }

    @Test
    void returnsNoSlotsWhenRequestedDateHasNone() {
        assertEquals(List.of(), service.availability("anan-saigon", TODAY.toString(), 2).slots());
    }

    @Test
    void rejectsInvalidAndOutOfWindowDates() {
        assertBad(() -> service.availability("anan-saigon", "28-09-2026", 2));
        assertEquals(List.of(), service.availability("anan-saigon", TODAY.plusDays(29).toString(), 2).slots());
        assertBad(() -> service.availability("anan-saigon", TODAY.plusDays(30).toString(), 2));
    }

    @Test
    void rejectsPastDateBeforeRestaurantAndSettingsLookup() {
        assertBad(() -> service.availability("anan-saigon", TODAY.minusDays(1).toString(), 2));

        verifyNoInteractions(restaurantCatalogPort, bookingSettingsPort, bookingSlotPort);
    }

    @Test
    void rejectsPartySizeBelowMinimumAndHidesSlotsAboveMaximumOnlineSize() {
        given(bookingSettingsPort.findByRestaurantId(restaurantId))
                .willReturn(Optional.of(settings(ConfirmationMode.AUTO, null, 2, 4, 5)));
        givenSlots(slot("11:30", 10, 0));

        assertBad(() -> service.availability("anan-saigon", TODAY.toString(), 0));
        assertBad(() -> service.availability("anan-saigon", TODAY.toString(), 1));
        RestaurantAvailabilityResponse response = service.availability("anan-saigon", TODAY.toString(), 5);
        assertEquals(List.of(), response.slots());
        assertEquals(true, response.requiresRestaurantConfirmation());
    }

    @Test
    void appliesAutoManualHybridAndLargePartyConfirmationRules() {
        assertEquals(false, service.availability("anan-saigon", TODAY.toString(), 2).requiresRestaurantConfirmation());

        given(bookingSettingsPort.findByRestaurantId(restaurantId))
                .willReturn(Optional.of(settings(ConfirmationMode.MANUAL, null, 1, 6, 7)));
        assertEquals(true, service.availability("anan-saigon", TODAY.toString(), 2).requiresRestaurantConfirmation());

        given(bookingSettingsPort.findByRestaurantId(restaurantId))
                .willReturn(Optional.of(settings(ConfirmationMode.HYBRID, 4, 1, 6, 7)));
        assertEquals(false, service.availability("anan-saigon", TODAY.toString(), 3).requiresRestaurantConfirmation());
        assertEquals(true, service.availability("anan-saigon", TODAY.toString(), 4).requiresRestaurantConfirmation());
        assertEquals(true, service.availability("anan-saigon", TODAY.toString(), 7).requiresRestaurantConfirmation());
    }

    @Test
    void reportsUnknownRestaurantAndMissingSettings() {
        given(restaurantCatalogPort.findActiveRestaurantBySlug("unknown")).willReturn(Optional.empty());
        assertThrows(RestaurantNotFoundException.class, () -> service.availability("unknown", TODAY.toString(), 2));

        given(bookingSettingsPort.findByRestaurantId(restaurantId)).willReturn(Optional.empty());
        assertThrows(BookingSettingsNotConfiguredException.class, () -> service.availability("anan-saigon", TODAY.toString(), 2));
    }

    private void givenSlots(BookingSlotCapacity... slots) {
        given(bookingSlotPort.findSlotCapacities(eq(restaurantId), any(), any())).willReturn(List.of(slots));
    }

    private BookingSlotCapacity slot(String localTime, int total, int reserved) {
        Instant startsAt = TODAY.atTime(LocalTime.parse(localTime))
                .atZone(BookingTime.RESTAURANT_TIME_ZONE)
                .toInstant();
        return new BookingSlotCapacity(startsAt, total, reserved);
    }

    private RestaurantData restaurant(String slug) {
        return new RestaurantData(restaurantId, slug, "", "", "", "", "", "", "", "", "");
    }

    private BookingSettings settings(
            ConfirmationMode mode, Integer hybridThreshold, int minimum, int maximumOnline, int largeThreshold) {
        return new BookingSettings(
                restaurantId, 20, 15, 90, mode, hybridThreshold, 30, minimum, maximumOnline, largeThreshold);
    }

    private void assertBad(Runnable action) {
        assertThrows(InvalidBookingRequestException.class, action::run);
    }
}
