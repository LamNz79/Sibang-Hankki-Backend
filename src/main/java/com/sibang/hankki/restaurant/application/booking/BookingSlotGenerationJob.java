package com.sibang.hankki.restaurant.application.booking;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantBookingSettings;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantEntity;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantBookingSettingsRepository;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantCatalogRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BookingSlotGenerationJob {

    private static final Logger log = LoggerFactory.getLogger(BookingSlotGenerationJob.class);

    private final RestaurantCatalogRepository restaurantRepository;
    private final RestaurantBookingSettingsRepository settingsRepository;
    private final BookingSlotGenerationService generationService;
    private final Clock clock;

    BookingSlotGenerationJob(
            RestaurantCatalogRepository restaurantRepository,
            RestaurantBookingSettingsRepository settingsRepository,
            BookingSlotGenerationService generationService,
            Clock clock) {
        this.restaurantRepository = restaurantRepository;
        this.settingsRepository = settingsRepository;
        this.generationService = generationService;
        this.clock = clock;
    }

    public int run() {
        LocalDate today = LocalDate.now(clock.withZone(BookingSlotGenerationService.RESTAURANT_TIME_ZONE));
        List<RestaurantEntity> restaurants = restaurantRepository.findAllActive();
        int generatedSlots = 0;
        int failures = 0;
        log.info("event=booking_slot_generation_started date={} restaurants={}", today, restaurants.size());

        for (RestaurantEntity restaurant : restaurants) {
            try {
                RestaurantBookingSettings settings = settingsRepository.findById(restaurant.getId()).orElse(null);
                if (settings == null) {
                    log.info("event=booking_slot_generation_skipped restaurantId={} slug={} reason=missing_settings",
                            restaurant.getId(), restaurant.getSlug());
                    continue;
                }
                int created = generationService.generateSlots(
                        restaurant.getId(), today, today.plusDays(settings.getBookingWindowDays() - 1L)).size();
                generatedSlots += created;
                log.info("event=booking_slot_generation_restaurant_completed restaurantId={} slug={} generatedSlots={}",
                        restaurant.getId(), restaurant.getSlug(), created);
            } catch (RuntimeException exception) {
                failures++;
                log.error("event=booking_slot_generation_restaurant_failed restaurantId={} slug={}",
                        restaurant.getId(), restaurant.getSlug(), exception);
            }
        }

        log.info("event=booking_slot_generation_completed date={} generatedSlots={} failures={}",
                today, generatedSlots, failures);
        return generatedSlots;
    }
}
