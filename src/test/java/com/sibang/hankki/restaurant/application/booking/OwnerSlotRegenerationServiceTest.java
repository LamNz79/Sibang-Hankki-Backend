package com.sibang.hankki.restaurant.application.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.restaurant.application.port.in.OwnerSlotRegenerationUseCase;
import com.sibang.hankki.restaurant.application.port.out.BookingSettings;
import com.sibang.hankki.restaurant.application.port.out.BookingSettingsPort;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotPort;
import com.sibang.hankki.restaurant.domain.booking.BookingSlotCandidate;
import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OwnerSlotRegenerationServiceTest {

    @Test
    void deletesOnlyTheGeneratedRangeThenCreatesMissingSlots() {
        UUID restaurantId = UUID.randomUUID();
        BookingSettingsPort settingsPort = org.mockito.Mockito.mock(BookingSettingsPort.class);
        BookingSlotPort slotPort = org.mockito.Mockito.mock(BookingSlotPort.class);
        BookingSlotGenerationService generationService =
                org.mockito.Mockito.mock(BookingSlotGenerationService.class);
        LocalDate today = LocalDate.of(2026, 10, 6);
        Clock clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        given(settingsPort.findByRestaurantId(restaurantId)).willReturn(java.util.Optional.of(new BookingSettings(
                restaurantId, 48, 30, 90, ConfirmationMode.AUTO, null, 30, 1, 6, 10)));
        given(slotPort.deleteUnreferencedSlots(restaurantId,
                Instant.parse("2026-10-05T17:00:00Z"), Instant.parse("2026-11-04T17:00:00Z")))
                .willReturn(12);
        given(generationService.generateSlots(restaurantId, today, today.plusDays(29)))
                .willReturn(List.of(new BookingSlotCandidate(
                        Instant.parse("2026-10-06T04:30:00Z"), Instant.parse("2026-10-06T06:00:00Z"))));

        OwnerSlotRegenerationUseCase.Result result = new OwnerSlotRegenerationService(
                settingsPort, slotPort, generationService, clock).regenerate(restaurantId);

        assertThat(result).isEqualTo(new OwnerSlotRegenerationUseCase.Result(12, 1));
        verify(generationService).generateSlots(restaurantId, today, today.plusDays(29));
    }
}
