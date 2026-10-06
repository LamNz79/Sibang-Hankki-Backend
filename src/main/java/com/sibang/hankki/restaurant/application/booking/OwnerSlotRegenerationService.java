package com.sibang.hankki.restaurant.application.booking;

import com.sibang.hankki.restaurant.application.exception.BookingSettingsNotConfiguredException;
import com.sibang.hankki.restaurant.application.port.in.OwnerSlotRegenerationUseCase;
import com.sibang.hankki.restaurant.application.port.out.BookingSettings;
import com.sibang.hankki.restaurant.application.port.out.BookingSettingsPort;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotPort;
import com.sibang.hankki.restaurant.domain.booking.BookingTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerSlotRegenerationService implements OwnerSlotRegenerationUseCase {

    private final BookingSettingsPort settingsPort;
    private final BookingSlotPort slotPort;
    private final BookingSlotGenerationService generationService;
    private final Clock clock;

    public OwnerSlotRegenerationService(
            BookingSettingsPort settingsPort,
            BookingSlotPort slotPort,
            BookingSlotGenerationService generationService,
            Clock clock) {
        this.settingsPort = settingsPort;
        this.slotPort = slotPort;
        this.generationService = generationService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result regenerate(UUID restaurantId) {
        BookingSettings settings = settingsPort.findByRestaurantId(restaurantId)
                .orElseThrow(BookingSettingsNotConfiguredException::new);
        LocalDate fromDate = LocalDate.now(clock.withZone(BookingTime.RESTAURANT_TIME_ZONE));
        LocalDate toDate = fromDate.plusDays(settings.bookingWindowDays() - 1L);
        Instant startsAt = fromDate.atStartOfDay(BookingTime.RESTAURANT_TIME_ZONE).toInstant();
        Instant endsAt = toDate.plusDays(1).atStartOfDay(BookingTime.RESTAURANT_TIME_ZONE).toInstant();
        int deleted = slotPort.deleteUnreferencedSlots(restaurantId, startsAt, endsAt);
        int generated = generationService.generateSlots(restaurantId, fromDate, toDate).size();
        return new Result(deleted, generated);
    }
}
