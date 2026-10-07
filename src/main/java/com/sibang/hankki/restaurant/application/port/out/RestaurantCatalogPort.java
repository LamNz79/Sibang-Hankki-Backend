package com.sibang.hankki.restaurant.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantCatalogPort {
    List<RestaurantData> findAllActiveRestaurants();

    Optional<RestaurantData> findActiveRestaurantBySlug(String slug);

    Optional<RestaurantData> findActiveRestaurantById(UUID restaurantId);

    Optional<RestaurantData> findRestaurantById(UUID restaurantId);

    List<RestaurantTagData> findTagsByRestaurantIds(Collection<UUID> restaurantIds);

    List<RestaurantBusinessHourData> findBusinessHoursByRestaurantIds(Collection<UUID> restaurantIds);

    List<RestaurantGalleryCount> countActiveImagesByRestaurantIds(Collection<UUID> restaurantIds);

    List<RestaurantImageData> findImagesByRestaurantIds(Collection<UUID> restaurantIds);
}
