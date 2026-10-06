package com.sibang.hankki.restaurant.application.port.out;

import com.sibang.hankki.restaurant.application.model.OwnerSettings;
import com.sibang.hankki.restaurant.application.model.OwnerSettingsUpdate;
import java.util.Optional;
import java.util.UUID;

public interface OwnerSettingsPort {

    Optional<OwnerSettings> findByRestaurantId(UUID restaurantId);

    OwnerSettings update(UUID restaurantId, OwnerSettingsUpdate update);

}
