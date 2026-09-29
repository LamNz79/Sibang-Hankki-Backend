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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class RestaurantAvailabilityServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    private final UUID restaurantId = UUID.randomUUID();
    private RestaurantCatalogRepository restaurantRepository;
    private RestaurantBookingSettingsRepository settingsRepository;
    private BookingSlotRepository slotRepository;
    private RestaurantAvailabilityService service;

    @BeforeEach
    void setUp() {
        restaurantRepository = mock(RestaurantCatalogRepository.class);
        settingsRepository = mock(RestaurantBookingSettingsRepository.class);
        slotRepository = mock(BookingSlotRepository.class);
        RestaurantEntity restaurant = mock(RestaurantEntity.class);
        given(restaurant.getId()).willReturn(restaurantId);
        given(restaurantRepository.findActiveBySlug("anan-saigon")).willReturn(Optional.of(restaurant));
        given(settingsRepository.findById(restaurantId)).willReturn(Optional.of(settings(ConfirmationMode.AUTO, null, 1, 6, 7)));
        givenSlots();
        service = new RestaurantAvailabilityService(
                restaurantRepository,
                settingsRepository,
                slotRepository,
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
    void returnsNoSlotsWhenRequestedDateHasNone() {
        assertEquals(List.of(), service.availability("anan-saigon", TODAY.toString(), 2).slots());
    }

    @Test
    void rejectsInvalidPastAndOutOfWindowDates() {
        assertBad(() -> service.availability("anan-saigon", "28-09-2026", 2));
        assertBad(() -> service.availability("anan-saigon", TODAY.minusDays(1).toString(), 2));
        assertEquals(List.of(), service.availability("anan-saigon", TODAY.plusDays(29).toString(), 2).slots());
        assertBad(() -> service.availability("anan-saigon", TODAY.plusDays(30).toString(), 2));
    }

    @Test
    void rejectsPartySizeBelowMinimumAndHidesSlotsAboveMaximumOnlineSize() {
        given(settingsRepository.findById(restaurantId)).willReturn(Optional.of(settings(ConfirmationMode.AUTO, null, 2, 4, 5)));
        givenSlots(slot("11:30", 10, 0));

        assertBad(() -> service.availability("anan-saigon", TODAY.toString(), 1));
        RestaurantAvailabilityResponse response = service.availability("anan-saigon", TODAY.toString(), 5);
        assertEquals(List.of(), response.slots());
        assertEquals(true, response.requiresRestaurantConfirmation());
    }

    @Test
    void appliesAutoManualHybridAndLargePartyConfirmationRules() {
        assertEquals(false, service.availability("anan-saigon", TODAY.toString(), 2).requiresRestaurantConfirmation());

        given(settingsRepository.findById(restaurantId)).willReturn(Optional.of(settings(ConfirmationMode.MANUAL, null, 1, 6, 7)));
        assertEquals(true, service.availability("anan-saigon", TODAY.toString(), 2).requiresRestaurantConfirmation());

        given(settingsRepository.findById(restaurantId)).willReturn(Optional.of(settings(ConfirmationMode.HYBRID, (short) 4, 1, 6, 7)));
        assertEquals(false, service.availability("anan-saigon", TODAY.toString(), 3).requiresRestaurantConfirmation());
        assertEquals(true, service.availability("anan-saigon", TODAY.toString(), 4).requiresRestaurantConfirmation());
        assertEquals(true, service.availability("anan-saigon", TODAY.toString(), 7).requiresRestaurantConfirmation());
    }

    @Test
    void reportsUnknownRestaurantAndMissingSettings() {
        given(restaurantRepository.findActiveBySlug("unknown")).willReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.availability("unknown", TODAY.toString(), 2));

        given(settingsRepository.findById(restaurantId)).willReturn(Optional.empty());
        assertStatus(HttpStatus.CONFLICT, () -> service.availability("anan-saigon", TODAY.toString(), 2));
    }

    private void givenSlots(BookingSlot... slots) {
        given(slotRepository.findByRestaurantIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
                eq(restaurantId), any(), any())).willReturn(List.of(slots));
    }

    private BookingSlot slot(String localTime, int total, int reserved) {
        Instant startsAt = TODAY.atTime(LocalTime.parse(localTime))
                .atZone(BookingSlotGenerationService.RESTAURANT_TIME_ZONE)
                .toInstant();
        return new BookingSlot(restaurantId, startsAt, startsAt.plusSeconds(3600), total, reserved);
    }

    private RestaurantBookingSettings settings(
            ConfirmationMode mode, Short hybridThreshold, int minimum, int maximumOnline, int largeThreshold) {
        return new RestaurantBookingSettings(
                restaurantId, 20, (short) 15, (short) 90, (short) 10, 30, mode, hybridThreshold,
                (short) 30, (short) minimum, (short) maximumOnline, (short) largeThreshold, 120, 15);
    }

    private void assertBad(Runnable action) {
        assertStatus(HttpStatus.BAD_REQUEST, action);
    }

    private void assertStatus(HttpStatus status, Runnable action) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(status, exception.getStatusCode());
    }
}
