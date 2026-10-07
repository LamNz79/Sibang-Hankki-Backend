package com.sibang.hankki.restaurant.application.port.in;

import com.sibang.hankki.restaurant.application.model.OwnerMedia;
import java.util.UUID;

public interface OwnerMediaUseCase {
    OwnerMedia get(UUID restaurantId);

    OwnerMedia.Image create(UUID restaurantId, OwnerMedia.Update update);

    OwnerMedia.Image update(UUID restaurantId, UUID id, OwnerMedia.Update update);

    void delete(UUID restaurantId, UUID id);
}
