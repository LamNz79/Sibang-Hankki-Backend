package com.sibang.hankki.restaurant.application.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.restaurant.application.port.in.OwnerSlotRegenerationUseCase;
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
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OwnerSlotRegenerationServiceTest {

    @Test
    void deletesOnlyTheGeneratedRangeThenCreatesMissingSlots() {
        UUID restaurantId = UUID.randomUUID();
        BookingSettingsPort settingsPort = org.mockito.Mockito.mock(BookingSettingsPort.class);
        RestaurantCatalogPort restaurantPort = org.mockito.Mockito.mock(RestaurantCatalogPort.class);
        BookingSlotGenerationService generationService =
                org.mockito.Mockito.mock(BookingSlotGenerationService.class);
        LocalDate today = LocalDate.of(2026, 10, 6);
        Clock clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        given(settingsPort.findByRestaurantId(restaurantId)).willReturn(java.util.Optional.of(new BookingSettings(
                restaurantId, 48, 30, 90, ConfirmationMode.AUTO, null, 30, 1, 6, 10)));
        given(restaurantPort.findActiveRestaurantById(restaurantId)).willReturn(java.util.Optional.of(
                restaurant(restaurantId, "Asia/Ho_Chi_Minh")));
        given(generationService.regenerateSlots(restaurantId, today, today.plusDays(29)))
                .willReturn(new BookingSlotGenerationService.GenerationResult(12, List.of(new BookingSlotCandidate(
                        java.time.Instant.parse("2026-10-06T04:30:00Z"),
                        java.time.Instant.parse("2026-10-06T06:00:00Z")))));

        OwnerSlotRegenerationUseCase.Result result = new OwnerSlotRegenerationService(
                settingsPort, restaurantPort, generationService, clock).regenerate(restaurantId);

        assertThat(result).isEqualTo(new OwnerSlotRegenerationUseCase.Result(12, 1));
        verify(generationService).regenerateSlots(restaurantId, today, today.plusDays(29));
    }

    @Test
    void administrationRegenerationUsesSelectedTimezone() {
        UUID restaurantId = UUID.randomUUID();
        BookingSettingsPort settingsPort = org.mockito.Mockito.mock(BookingSettingsPort.class);
        RestaurantCatalogPort restaurantPort = org.mockito.Mockito.mock(RestaurantCatalogPort.class);
        BookingSlotGenerationService generationService =
                org.mockito.Mockito.mock(BookingSlotGenerationService.class);
        ZoneId timeZone = ZoneId.of("Asia/Bangkok");
        Clock clock = Clock.fixed(Instant.parse("2026-10-05T17:30:00Z"), ZoneOffset.UTC);
        LocalDate today = LocalDate.of(2026, 10, 6);
        given(settingsPort.findByRestaurantId(restaurantId)).willReturn(java.util.Optional.of(new BookingSettings(
                restaurantId, 48, 30, 90, ConfirmationMode.AUTO, null, 2, 1, 6, 10)));
        given(generationService.regenerateSlotsForAdministration(
                restaurantId, today, today.plusDays(1), timeZone))
                .willReturn(new BookingSlotGenerationService.GenerationResult(0, List.of()));

        new OwnerSlotRegenerationService(settingsPort, restaurantPort, generationService, clock)
                .regenerateForAdministration(restaurantId, timeZone);

        verify(generationService).regenerateSlotsForAdministration(
                restaurantId, today, today.plusDays(1), timeZone);
    }

    @Test
    void ownerRegenerationUsesPersistedRestaurantTimezone() {
        UUID restaurantId = UUID.randomUUID();
        BookingSettingsPort settingsPort = org.mockito.Mockito.mock(BookingSettingsPort.class);
        RestaurantCatalogPort restaurantPort = org.mockito.Mockito.mock(RestaurantCatalogPort.class);
        BookingSlotGenerationService generationService =
                org.mockito.Mockito.mock(BookingSlotGenerationService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-05T16:00:00Z"), ZoneOffset.UTC);
        LocalDate seoulToday = LocalDate.of(2026, 10, 6);
        given(restaurantPort.findActiveRestaurantById(restaurantId)).willReturn(java.util.Optional.of(
                restaurant(restaurantId, "Asia/Seoul")));
        given(settingsPort.findByRestaurantId(restaurantId)).willReturn(java.util.Optional.of(new BookingSettings(
                restaurantId, 48, 30, 90, ConfirmationMode.AUTO, null, 2, 1, 6, 10)));
        given(generationService.regenerateSlots(
                restaurantId, seoulToday, seoulToday.plusDays(1)))
                .willReturn(new BookingSlotGenerationService.GenerationResult(0, List.of()));

        new OwnerSlotRegenerationService(settingsPort, restaurantPort, generationService, clock)
                .regenerate(restaurantId);

        verify(generationService).regenerateSlots(
                restaurantId, seoulToday, seoulToday.plusDays(1));
    }

    private RestaurantData restaurant(UUID restaurantId, String timezone) {
        return new RestaurantData(
                restaurantId, "restaurant", "Restaurant", "", "", "", "", "", "", "", "", timezone);
    }
}
