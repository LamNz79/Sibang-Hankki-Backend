package com.sibang.hankki.restaurant.application;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantBookingSettings;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantEntity;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantBookingSettingsRepository;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantCatalogRepository;
import com.sibang.hankki.restaurant.application.exception.BookingSettingsNotConfiguredException;
import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;

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
        UUID restaurantId = restaurantRepository.findActiveBySlug(slug)
                .map(RestaurantEntity::getId)
                .orElseThrow(RestaurantNotFoundException::new);
        return getByRestaurantId(restaurantId);
    }
}
