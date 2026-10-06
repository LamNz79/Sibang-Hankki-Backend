package com.sibang.hankki.restaurant.application.port.in;

import com.sibang.hankki.restaurant.application.model.OwnerSettings;
import com.sibang.hankki.restaurant.application.model.OwnerSettingsUpdate;
import java.util.UUID;

public interface OwnerSettingsUseCase {

    OwnerSettings get(UUID restaurantId);

    OwnerSettings update(UUID restaurantId, OwnerSettingsUpdate update);
}
