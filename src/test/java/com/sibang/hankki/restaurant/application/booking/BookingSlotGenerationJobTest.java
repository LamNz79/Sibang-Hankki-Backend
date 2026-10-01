package com.sibang.hankki.restaurant.application.booking;

import com.sibang.hankki.restaurant.application.port.out.BookingSettings;
import com.sibang.hankki.restaurant.application.port.out.BookingSettingsPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantData;
import com.sibang.hankki.restaurant.domain.booking.BookingSlotCandidate;
import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BookingSlotGenerationJobTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    private RestaurantCatalogPort restaurantCatalogPort;
    private BookingSettingsPort bookingSettingsPort;
    private BookingSlotGenerationService generationService;
    private BookingSlotGenerationJob job;

    @BeforeEach
    void setUp() {
        restaurantCatalogPort = mock(RestaurantCatalogPort.class);
        bookingSettingsPort = mock(BookingSettingsPort.class);
        generationService = mock(BookingSlotGenerationService.class);
        job = new BookingSlotGenerationJob(
                restaurantCatalogPort,
                bookingSettingsPort,
                generationService,
                Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    @Test
    void generatesFromTodayThroughTheInclusiveBookingWindow() {
        RestaurantData restaurant = restaurant("anan-saigon");
        given(restaurantCatalogPort.findAllActiveRestaurants()).willReturn(List.of(restaurant));
        given(bookingSettingsPort.findByRestaurantId(restaurant.id())).willReturn(Optional.of(settings(restaurant.id(), 30)));
        given(generationService.generateSlots(eq(restaurant.id()), eq(TODAY), eq(TODAY.plusDays(29))))
                .willReturn(List.of(slot(), slot()));

        assertEquals(2, job.run());
        verify(generationService).generateSlots(restaurant.id(), TODAY, TODAY.plusDays(29));
    }

    @Test
    void skipsRestaurantsWithoutSettings() {
        RestaurantData restaurant = restaurant("no-settings");
        given(restaurantCatalogPort.findAllActiveRestaurants()).willReturn(List.of(restaurant));
        given(bookingSettingsPort.findByRestaurantId(restaurant.id())).willReturn(Optional.empty());

        assertEquals(0, job.run());
        verify(generationService, never()).generateSlots(any(), any(), any());
    }

    @Test
    void processesOnlyActiveRestaurantsReturnedByThePort() {
        given(restaurantCatalogPort.findAllActiveRestaurants()).willReturn(List.of());

        assertEquals(0, job.run());
        verify(bookingSettingsPort, never()).findByRestaurantId(any());
        verify(generationService, never()).generateSlots(any(), any(), any());
    }

    @Test
    void continuesAfterOneRestaurantFailsAndReturnsTheOtherGeneratedCount() {
        RestaurantData failing = restaurant("failing");
        RestaurantData succeeding = restaurant("succeeding");
        given(restaurantCatalogPort.findAllActiveRestaurants()).willReturn(List.of(failing, succeeding));
        given(bookingSettingsPort.findByRestaurantId(failing.id())).willReturn(Optional.of(settings(failing.id(), 1)));
        given(bookingSettingsPort.findByRestaurantId(succeeding.id())).willReturn(Optional.of(settings(succeeding.id(), 1)));
        given(generationService.generateSlots(failing.id(), TODAY, TODAY)).willThrow(new IllegalStateException("failure"));
        given(generationService.generateSlots(succeeding.id(), TODAY, TODAY)).willReturn(List.of(slot()));

        assertEquals(1, job.run());
        verify(generationService).generateSlots(succeeding.id(), TODAY, TODAY);
    }

    private RestaurantData restaurant(String slug) {
        return new RestaurantData(UUID.randomUUID(), slug, "", "", "", "", "", "", "", "", "");
    }

    private BookingSettings settings(UUID restaurantId, int bookingWindowDays) {
        return new BookingSettings(
                restaurantId, 20, 60, 90, ConfirmationMode.HYBRID, 7,
                bookingWindowDays, 1, 10, 11);
    }

    private BookingSlotCandidate slot() {
        return new BookingSlotCandidate(Instant.parse("2026-09-28T03:00:00Z"), Instant.parse("2026-09-28T04:30:00Z"));
    }
}
