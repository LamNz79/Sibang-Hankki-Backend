package com.sibang.hankki.restaurant.application.booking;

import com.sibang.hankki.restaurant.application.port.in.BookingSlotGenerationUseCase;
import com.sibang.hankki.restaurant.application.port.out.BookingSettings;
import com.sibang.hankki.restaurant.application.port.out.BookingSettingsPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantData;
import com.sibang.hankki.restaurant.domain.booking.BookingTime;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BookingSlotGenerationJob implements BookingSlotGenerationUseCase {

    private static final Logger log = LoggerFactory.getLogger(BookingSlotGenerationJob.class);

    private final RestaurantCatalogPort restaurantCatalogPort;
    private final BookingSettingsPort bookingSettingsPort;
    private final BookingSlotGenerationService generationService;
    private final Clock clock;

    BookingSlotGenerationJob(
            RestaurantCatalogPort restaurantCatalogPort,
            BookingSettingsPort bookingSettingsPort,
            BookingSlotGenerationService generationService,
            Clock clock) {
        this.restaurantCatalogPort = restaurantCatalogPort;
        this.bookingSettingsPort = bookingSettingsPort;
        this.generationService = generationService;
        this.clock = clock;
    }

    @Override
    public int run() {
        LocalDate today = LocalDate.now(clock.withZone(BookingTime.RESTAURANT_TIME_ZONE));
        List<RestaurantData> restaurants = restaurantCatalogPort.findAllActiveRestaurants();
        int generatedSlots = 0;
        int failures = 0;
        log.info("event=booking_slot_generation_started date={} restaurants={}", today, restaurants.size());

        for (RestaurantData restaurant : restaurants) {
            try {
                BookingSettings settings = bookingSettingsPort.findByRestaurantId(restaurant.id()).orElse(null);
                if (settings == null) {
                    log.info("event=booking_slot_generation_skipped restaurantId={} slug={} reason=missing_settings",
                            restaurant.id(), restaurant.slug());
                    continue;
                }
                int created = generationService.generateSlots(
                        restaurant.id(), today, today.plusDays(settings.bookingWindowDays() - 1L)).size();
                generatedSlots += created;
                log.info("event=booking_slot_generation_restaurant_completed restaurantId={} slug={} generatedSlots={}",
                        restaurant.id(), restaurant.slug(), created);
            } catch (RuntimeException exception) {
                failures++;
                log.error("event=booking_slot_generation_restaurant_failed restaurantId={} slug={}",
                        restaurant.id(), restaurant.slug(), exception);
            }
        }

        log.info("event=booking_slot_generation_completed date={} generatedSlots={} failures={}",
                today, generatedSlots, failures);
        return generatedSlots;
    }
}
