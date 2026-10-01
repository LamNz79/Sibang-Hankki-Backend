package com.sibang.hankki.restaurant.application.port.in;

import com.sibang.hankki.restaurant.application.model.RestaurantResponse;
import com.sibang.hankki.restaurant.application.model.RestaurantSummaryResponse;
import java.util.List;
import java.util.Optional;

public interface RestaurantCatalogUseCase {
    List<RestaurantSummaryResponse> restaurants();

    Optional<RestaurantResponse> restaurant(String slug);
}
