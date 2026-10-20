package com.sibang.hankki.favorite.application.port.out;

import java.util.List;
import java.util.UUID;

public interface CustomerFavoritePort {

    List<UUID> findActiveRestaurantIds(UUID userId);

    void save(UUID userId, UUID restaurantId);

    void delete(UUID userId, UUID restaurantId);
}
