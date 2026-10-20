package com.sibang.hankki.restaurant.application.port.in;

import com.sibang.hankki.restaurant.application.model.RestaurantResponse;
import com.sibang.hankki.restaurant.application.model.RestaurantSummaryResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantCatalogUseCase {
    List<RestaurantSummaryResponse> restaurants(Integer provinceCode, Integer wardCode);

    List<RestaurantSummaryResponse> restaurantSummaries(List<UUID> restaurantIds);

    Optional<RestaurantResponse> restaurant(String slug);
}
