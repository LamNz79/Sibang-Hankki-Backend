package com.sibang.hankki.restaurant.application.booking;

import com.sibang.hankki.restaurant.application.exception.BookingSettingsNotConfiguredException;
import com.sibang.hankki.restaurant.application.port.in.OwnerSlotRegenerationUseCase;
import com.sibang.hankki.restaurant.application.port.out.BookingSettings;
import com.sibang.hankki.restaurant.application.port.out.BookingSettingsPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;
import com.sibang.hankki.restaurant.domain.booking.BookingTime;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerSlotRegenerationService implements OwnerSlotRegenerationUseCase {

    private final BookingSettingsPort settingsPort;
    private final RestaurantCatalogPort restaurantCatalogPort;
    private final BookingSlotGenerationService generationService;
    private final Clock clock;

    public OwnerSlotRegenerationService(
            BookingSettingsPort settingsPort,
            RestaurantCatalogPort restaurantCatalogPort,
            BookingSlotGenerationService generationService,
            Clock clock) {
        this.settingsPort = settingsPort;
        this.restaurantCatalogPort = restaurantCatalogPort;
        this.generationService = generationService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result regenerate(UUID restaurantId) {
        ZoneId timeZone = BookingTime.restaurantTimeZone(restaurantCatalogPort.findActiveRestaurantById(restaurantId)
                .orElseThrow(RestaurantNotFoundException::new)
                .timezone());
        return regenerate(restaurantId, timeZone, false);
    }

    @Override
    @Transactional
    public Result regenerateForAdministration(UUID restaurantId, ZoneId timeZone) {
        return regenerate(restaurantId, timeZone, true);
    }

    private Result regenerate(UUID restaurantId, ZoneId timeZone, boolean administration) {
        BookingSettings settings = settingsPort.findByRestaurantId(restaurantId)
                .orElseThrow(BookingSettingsNotConfiguredException::new);
        LocalDate fromDate = LocalDate.now(clock.withZone(timeZone));
        LocalDate toDate = fromDate.plusDays(settings.bookingWindowDays() - 1L);
        BookingSlotGenerationService.GenerationResult result = administration
                ? generationService.regenerateSlotsForAdministration(restaurantId, fromDate, toDate, timeZone)
                : generationService.regenerateSlots(restaurantId, fromDate, toDate);
        return new Result(result.deletedSlots(), result.generatedSlots().size());
    }
}
