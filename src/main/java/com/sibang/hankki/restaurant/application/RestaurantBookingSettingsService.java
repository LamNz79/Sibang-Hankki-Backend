package com.sibang.hankki.restaurant.application;

import com.sibang.hankki.restaurant.application.exception.BookingSettingsNotConfiguredException;
import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;
import com.sibang.hankki.restaurant.application.port.out.BookingSettings;
import com.sibang.hankki.restaurant.application.port.out.BookingSettingsPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class RestaurantBookingSettingsService {

    private final BookingSettingsPort bookingSettingsPort;
    private final RestaurantCatalogPort restaurantCatalogPort;

    RestaurantBookingSettingsService(
            BookingSettingsPort bookingSettingsPort,
            RestaurantCatalogPort restaurantCatalogPort) {
        this.bookingSettingsPort = bookingSettingsPort;
        this.restaurantCatalogPort = restaurantCatalogPort;
    }

    BookingSettings getByRestaurantId(UUID restaurantId) {
        return bookingSettingsPort.findByRestaurantId(restaurantId)
                .orElseThrow(BookingSettingsNotConfiguredException::new);
    }

    BookingSettings getByRestaurantSlug(String slug) {
        UUID restaurantId = restaurantCatalogPort.findActiveRestaurantBySlug(slug)
                .map(restaurant -> restaurant.id())
                .orElseThrow(RestaurantNotFoundException::new);
        return getByRestaurantId(restaurantId);
    }
}
