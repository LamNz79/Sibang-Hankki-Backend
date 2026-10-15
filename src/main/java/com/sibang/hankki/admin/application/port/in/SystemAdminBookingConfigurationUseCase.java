package com.sibang.hankki.admin.application.port.in;

import com.sibang.hankki.restaurant.application.model.BookingSettingsUpdate;
import com.sibang.hankki.restaurant.application.model.OwnerSettings;
import com.sibang.hankki.restaurant.application.port.in.OwnerBusinessHoursUseCase;
import com.sibang.hankki.restaurant.application.port.in.OwnerSlotRegenerationUseCase;
import com.sibang.hankki.restaurant.application.port.out.RestaurantBusinessHourData;
import java.util.List;
import java.util.UUID;

public interface SystemAdminBookingConfigurationUseCase {

    OwnerSettings getBookingSettings(UUID restaurantId);

    OwnerSettings updateBookingSettings(UUID restaurantId, BookingSettingsUpdate update);

    List<RestaurantBusinessHourData> getBusinessHours(UUID restaurantId);

    OwnerBusinessHoursUseCase.UpdateResult updateBusinessHours(
            UUID restaurantId, List<RestaurantBusinessHourData> hours);

    OwnerSlotRegenerationUseCase.Result regenerateSlots(UUID restaurantId);
}
