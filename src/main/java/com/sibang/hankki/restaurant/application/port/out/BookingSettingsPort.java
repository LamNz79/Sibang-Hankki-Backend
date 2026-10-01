package com.sibang.hankki.restaurant.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface BookingSettingsPort {
    Optional<BookingSettings> findByRestaurantId(UUID restaurantId);
}
