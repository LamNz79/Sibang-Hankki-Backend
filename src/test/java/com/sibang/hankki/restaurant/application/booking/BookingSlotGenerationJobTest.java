package com.sibang.hankki.restaurant.application.booking;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.BookingSlot;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantBookingSettings;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantEntity;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantBookingSettingsRepository;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantCatalogRepository;
import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;

import java.time.Clock;
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

    private RestaurantCatalogRepository restaurantRepository;
    private RestaurantBookingSettingsRepository settingsRepository;
    private BookingSlotGenerationService generationService;
    private BookingSlotGenerationJob job;

    @BeforeEach
    void setUp() {
        restaurantRepository = mock(RestaurantCatalogRepository.class);
        settingsRepository = mock(RestaurantBookingSettingsRepository.class);
        generationService = mock(BookingSlotGenerationService.class);
        job = new BookingSlotGenerationJob(
                restaurantRepository,
                settingsRepository,
                generationService,
                Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    @Test
    void generatesFromTodayThroughTheInclusiveBookingWindow() {
        RestaurantFixture restaurant = restaurant("anan-saigon");
        BookingSlot firstSlot = mock(BookingSlot.class);
        BookingSlot secondSlot = mock(BookingSlot.class);
        given(restaurantRepository.findAllActive()).willReturn(List.of(restaurant.entity()));
        given(settingsRepository.findById(restaurant.id())).willReturn(Optional.of(settings(restaurant.id(), 30)));
        given(generationService.generateSlots(eq(restaurant.id()), eq(TODAY), eq(TODAY.plusDays(29))))
                .willReturn(List.of(firstSlot, secondSlot));

        assertEquals(2, job.run());
        verify(generationService).generateSlots(restaurant.id(), TODAY, TODAY.plusDays(29));
    }

    @Test
    void skipsRestaurantsWithoutSettings() {
        RestaurantFixture restaurant = restaurant("no-settings");
        given(restaurantRepository.findAllActive()).willReturn(List.of(restaurant.entity()));
        given(settingsRepository.findById(restaurant.id())).willReturn(Optional.empty());

        assertEquals(0, job.run());
        verify(generationService, never()).generateSlots(any(), any(), any());
    }

    @Test
    void processesOnlyActiveRestaurantsReturnedByTheRepository() {
        given(restaurantRepository.findAllActive()).willReturn(List.of());

        assertEquals(0, job.run());
        verify(settingsRepository, never()).findById(any());
        verify(generationService, never()).generateSlots(any(), any(), any());
    }

    @Test
    void continuesAfterOneRestaurantFailsAndReturnsTheOtherGeneratedCount() {
        RestaurantFixture failing = restaurant("failing");
        RestaurantFixture succeeding = restaurant("succeeding");
        BookingSlot generatedSlot = mock(BookingSlot.class);
        given(restaurantRepository.findAllActive()).willReturn(List.of(failing.entity(), succeeding.entity()));
        given(settingsRepository.findById(failing.id())).willReturn(Optional.of(settings(failing.id(), 1)));
        given(settingsRepository.findById(succeeding.id())).willReturn(Optional.of(settings(succeeding.id(), 1)));
        given(generationService.generateSlots(failing.id(), TODAY, TODAY)).willThrow(new IllegalStateException("failure"));
        given(generationService.generateSlots(succeeding.id(), TODAY, TODAY))
                .willReturn(List.of(generatedSlot));

        assertEquals(1, job.run());
        verify(generationService).generateSlots(succeeding.id(), TODAY, TODAY);
    }

    private RestaurantFixture restaurant(String slug) {
        RestaurantEntity entity = mock(RestaurantEntity.class);
        UUID id = UUID.randomUUID();
        given(entity.getId()).willReturn(id);
        given(entity.getSlug()).willReturn(slug);
        return new RestaurantFixture(entity, id);
    }

    private RestaurantBookingSettings settings(UUID restaurantId, int bookingWindowDays) {
        return new RestaurantBookingSettings(
                restaurantId, 20, (short) 60, (short) 90, (short) 10, 30, ConfirmationMode.HYBRID, (short) 7,
                (short) bookingWindowDays, (short) 1, (short) 10, (short) 11, 120, 15);
    }

    private record RestaurantFixture(RestaurantEntity entity, UUID id) {
    }
}
