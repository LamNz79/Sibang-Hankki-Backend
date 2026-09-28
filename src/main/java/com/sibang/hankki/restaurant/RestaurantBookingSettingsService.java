package com.sibang.hankki.restaurant;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class RestaurantBookingSettingsService {

    private final RestaurantBookingSettingsRepository settingsRepository;
    private final RestaurantCatalogRepository restaurantRepository;

    RestaurantBookingSettingsService(
            RestaurantBookingSettingsRepository settingsRepository,
            RestaurantCatalogRepository restaurantRepository) {
        this.settingsRepository = settingsRepository;
        this.restaurantRepository = restaurantRepository;
    }

    RestaurantBookingSettings getByRestaurantId(UUID restaurantId) {
        return settingsRepository.findById(restaurantId)
                .orElseThrow(BookingSettingsNotConfiguredException::new);
    }

    RestaurantBookingSettings getByRestaurantSlug(String slug) {
        return restaurantRepository.findActiveBySlug(slug)
                .map(RestaurantEntity::getId)
                .map(this::getByRestaurantId)
                .orElseThrow(BookingSettingsNotConfiguredException::new);
    }
}
