package com.sibang.hankki.admin.application;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.port.in.SystemAdminBookingConfigurationUseCase;
import com.sibang.hankki.admin.application.port.out.SystemAdminRestaurantPort;
import com.sibang.hankki.restaurant.application.model.BookingSettingsUpdate;
import com.sibang.hankki.restaurant.application.model.OwnerSettings;
import com.sibang.hankki.restaurant.application.port.in.OwnerBusinessHoursUseCase;
import com.sibang.hankki.restaurant.application.port.in.OwnerSettingsUseCase;
import com.sibang.hankki.restaurant.application.port.in.OwnerSlotRegenerationUseCase;
import com.sibang.hankki.restaurant.application.port.out.RestaurantBusinessHourData;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SystemAdminBookingConfigurationService implements SystemAdminBookingConfigurationUseCase {

    private final SystemAdminRestaurantPort restaurantPort;
    private final OwnerSettingsUseCase settingsUseCase;
    private final OwnerBusinessHoursUseCase businessHoursUseCase;
    private final OwnerSlotRegenerationUseCase regenerationUseCase;

    public SystemAdminBookingConfigurationService(
            SystemAdminRestaurantPort restaurantPort,
            OwnerSettingsUseCase settingsUseCase,
            OwnerBusinessHoursUseCase businessHoursUseCase,
            OwnerSlotRegenerationUseCase regenerationUseCase) {
        this.restaurantPort = restaurantPort;
        this.settingsUseCase = settingsUseCase;
        this.businessHoursUseCase = businessHoursUseCase;
        this.regenerationUseCase = regenerationUseCase;
    }

    @Override
    public OwnerSettings getBookingSettings(UUID restaurantId) {
        requireRestaurant(restaurantId);
        return settingsUseCase.getForAdministration(restaurantId);
    }

    @Override
    public OwnerSettings updateBookingSettings(UUID restaurantId, BookingSettingsUpdate update) {
        requireRestaurant(restaurantId);
        return settingsUseCase.updateForAdministration(restaurantId, update);
    }

    @Override
    public List<RestaurantBusinessHourData> getBusinessHours(UUID restaurantId) {
        requireRestaurant(restaurantId);
        return businessHoursUseCase.get(restaurantId);
    }

    @Override
    public OwnerBusinessHoursUseCase.UpdateResult updateBusinessHours(
            UUID restaurantId, List<RestaurantBusinessHourData> hours) {
        ZoneId timeZone = ZoneId.of(requireRestaurant(restaurantId).timezone());
        return businessHoursUseCase.updateForAdministration(restaurantId, hours, timeZone);
    }

    @Override
    public OwnerSlotRegenerationUseCase.Result regenerateSlots(UUID restaurantId) {
        ZoneId timeZone = ZoneId.of(requireRestaurant(restaurantId).timezone());
        return regenerationUseCase.regenerateForAdministration(restaurantId, timeZone);
    }

    private SystemAdminRestaurantPort.RestaurantProfileData requireRestaurant(UUID restaurantId) {
        if (restaurantId == null) {
            throw new AdminResourceNotFoundException("Restaurant not found");
        }
        return restaurantPort.findById(restaurantId)
                .orElseThrow(() -> new AdminResourceNotFoundException("Restaurant not found"));
    }
}
