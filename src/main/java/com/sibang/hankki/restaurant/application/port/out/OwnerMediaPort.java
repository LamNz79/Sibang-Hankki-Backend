package com.sibang.hankki.restaurant.application.port.out;

import com.sibang.hankki.restaurant.application.model.OwnerMedia;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OwnerMediaPort {
    List<OwnerMedia.Image> findAll(UUID restaurantId);

    OwnerMedia.Image create(UUID restaurantId, OwnerMedia.Update update);

    Optional<OwnerMedia.Image> update(UUID restaurantId, UUID id, OwnerMedia.Update update);

    boolean delete(UUID restaurantId, UUID id);
}
