package com.sibang.hankki.favorite.application.port.in;

import com.sibang.hankki.restaurant.application.model.RestaurantSummaryResponse;
import java.util.List;
import java.util.UUID;

public interface CustomerFavoriteUseCase {

    List<RestaurantSummaryResponse> favorites(UUID userId);

    void save(UUID userId, String restaurantSlug);

    void delete(UUID userId, String restaurantSlug);
}
