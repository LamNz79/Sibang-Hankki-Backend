package com.sibang.hankki.restaurant.application.port.in;

import com.sibang.hankki.restaurant.application.port.out.RestaurantBusinessHourData;
import java.util.List;
import java.util.UUID;

public interface OwnerBusinessHoursUseCase {
    List<RestaurantBusinessHourData> get(UUID restaurantId);

    UpdateResult update(UUID restaurantId, List<RestaurantBusinessHourData> hours);

    record UpdateResult(
            List<RestaurantBusinessHourData> hours,
            OwnerSlotRegenerationUseCase.Result regeneration) {
    }
}
