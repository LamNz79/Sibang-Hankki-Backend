package com.sibang.hankki.favorite.application;

import com.sibang.hankki.favorite.application.port.in.CustomerFavoriteUseCase;
import com.sibang.hankki.favorite.application.port.out.CustomerFavoritePort;
import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;
import com.sibang.hankki.restaurant.application.model.RestaurantSummaryResponse;
import com.sibang.hankki.restaurant.application.port.in.RestaurantCatalogUseCase;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantData;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerFavoriteService implements CustomerFavoriteUseCase {

    private final CustomerFavoritePort favoritePort;
    private final RestaurantCatalogPort catalogPort;
    private final RestaurantCatalogUseCase catalogUseCase;

    public CustomerFavoriteService(
            CustomerFavoritePort favoritePort,
            RestaurantCatalogPort catalogPort,
            RestaurantCatalogUseCase catalogUseCase) {
        this.favoritePort = favoritePort;
        this.catalogPort = catalogPort;
        this.catalogUseCase = catalogUseCase;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RestaurantSummaryResponse> favorites(UUID userId) {
        return catalogUseCase.restaurantSummaries(favoritePort.findActiveRestaurantIds(userId));
    }

    @Override
    @Transactional
    public void save(UUID userId, String restaurantSlug) {
        favoritePort.save(userId, activeRestaurant(restaurantSlug).id());
    }

    @Override
    @Transactional
    public void delete(UUID userId, String restaurantSlug) {
        favoritePort.delete(userId, activeRestaurant(restaurantSlug).id());
    }

    private RestaurantData activeRestaurant(String slug) {
        return catalogPort.findActiveRestaurantBySlug(slug).orElseThrow(RestaurantNotFoundException::new);
    }
}
