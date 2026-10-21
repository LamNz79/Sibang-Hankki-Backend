package com.sibang.hankki.restaurant.application.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.restaurant.application.port.in.OwnerSlotRegenerationUseCase;
import com.sibang.hankki.restaurant.application.port.out.BookingSettings;
import com.sibang.hankki.restaurant.application.port.out.BookingSettingsPort;
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
        BookingSlotGenerationService generationService =
                org.mockito.Mockito.mock(BookingSlotGenerationService.class);
        LocalDate today = LocalDate.of(2026, 10, 6);
        Clock clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        given(settingsPort.findByRestaurantId(restaurantId)).willReturn(java.util.Optional.of(new BookingSettings(
                restaurantId, 48, 30, 90, ConfirmationMode.AUTO, null, 30, 1, 6, 10)));
        given(generationService.regenerateSlots(restaurantId, today, today.plusDays(29)))
                .willReturn(new BookingSlotGenerationService.GenerationResult(12, List.of(new BookingSlotCandidate(
                        java.time.Instant.parse("2026-10-06T04:30:00Z"),
                        java.time.Instant.parse("2026-10-06T06:00:00Z")))));

        OwnerSlotRegenerationUseCase.Result result = new OwnerSlotRegenerationService(
                settingsPort, generationService, clock).regenerate(restaurantId);

        assertThat(result).isEqualTo(new OwnerSlotRegenerationUseCase.Result(12, 1));
        verify(generationService).regenerateSlots(restaurantId, today, today.plusDays(29));
    }

    @Test
    void administrationRegenerationUsesSelectedTimezone() {
        UUID restaurantId = UUID.randomUUID();
        BookingSettingsPort settingsPort = org.mockito.Mockito.mock(BookingSettingsPort.class);
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

        new OwnerSlotRegenerationService(settingsPort, generationService, clock)
                .regenerateForAdministration(restaurantId, timeZone);

        verify(generationService).regenerateSlotsForAdministration(
                restaurantId, today, today.plusDays(1), timeZone);
    }
}
