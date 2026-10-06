package com.sibang.hankki.restaurant.application.port.out;

import java.util.List;
import java.util.UUID;

public interface OwnerBusinessHoursPort {
    List<RestaurantBusinessHourData> findByRestaurantId(UUID restaurantId);

    void replace(UUID restaurantId, List<RestaurantBusinessHourData> hours);
}
