package com.sibang.hankki.restaurant.application.port.in;

import com.sibang.hankki.restaurant.application.port.out.RestaurantBusinessHourData;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public interface OwnerBusinessHoursUseCase {
    List<RestaurantBusinessHourData> get(UUID restaurantId);

    UpdateResult update(UUID restaurantId, List<RestaurantBusinessHourData> hours);

    UpdateResult updateForAdministration(
            UUID restaurantId, List<RestaurantBusinessHourData> hours, ZoneId timeZone);

    record UpdateResult(
            List<RestaurantBusinessHourData> hours,
            OwnerSlotRegenerationUseCase.Result regeneration) {
    }
}
