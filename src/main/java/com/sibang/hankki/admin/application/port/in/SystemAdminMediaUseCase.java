package com.sibang.hankki.admin.application.port.in;

import com.sibang.hankki.restaurant.application.model.OwnerMedia;
import java.util.UUID;

public interface SystemAdminMediaUseCase {

    OwnerMedia get(UUID restaurantId);

    OwnerMedia.Image create(UUID restaurantId, OwnerMedia.Update update);

    OwnerMedia.Image update(UUID restaurantId, UUID mediaId, OwnerMedia.Update update);

    void delete(UUID restaurantId, UUID mediaId);
}
