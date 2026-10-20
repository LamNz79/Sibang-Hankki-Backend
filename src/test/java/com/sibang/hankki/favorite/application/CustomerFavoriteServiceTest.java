package com.sibang.hankki.favorite.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.favorite.application.port.out.CustomerFavoritePort;
import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;
import com.sibang.hankki.restaurant.application.model.RestaurantSummaryResponse;
import com.sibang.hankki.restaurant.application.port.in.RestaurantCatalogUseCase;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantData;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerFavoriteServiceTest {

    private final CustomerFavoritePort favoritePort = org.mockito.Mockito.mock(CustomerFavoritePort.class);
    private final RestaurantCatalogPort catalogPort = org.mockito.Mockito.mock(RestaurantCatalogPort.class);
    private final RestaurantCatalogUseCase catalogUseCase = org.mockito.Mockito.mock(RestaurantCatalogUseCase.class);
    private final CustomerFavoriteService service =
            new CustomerFavoriteService(favoritePort, catalogPort, catalogUseCase);

    @Test
    void listsActiveFavoritesInPersistenceOrder() {
        UUID userId = UUID.randomUUID();
        List<UUID> restaurantIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        List<RestaurantSummaryResponse> summaries = List.of(summary("newest"), summary("older"));
        given(favoritePort.findActiveRestaurantIds(userId)).willReturn(restaurantIds);
        given(catalogUseCase.restaurantSummaries(restaurantIds)).willReturn(summaries);

        assertThat(service.favorites(userId)).isEqualTo(summaries);
    }

    @Test
    void saveAndDeleteResolveOnlyActiveRestaurantSlugs() {
        UUID userId = UUID.randomUUID();
        RestaurantData restaurant = restaurant("anan-saigon");
        given(catalogPort.findActiveRestaurantBySlug(restaurant.slug())).willReturn(Optional.of(restaurant));

        service.save(userId, restaurant.slug());
        service.delete(userId, restaurant.slug());

        verify(favoritePort).save(userId, restaurant.id());
        verify(favoritePort).delete(userId, restaurant.id());
    }

    @Test
    void unknownInactiveOrDeletedRestaurantIsNotFound() {
        given(catalogPort.findActiveRestaurantBySlug("unavailable")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(UUID.randomUUID(), "unavailable"))
                .isInstanceOf(RestaurantNotFoundException.class);
        assertThatThrownBy(() -> service.delete(UUID.randomUUID(), "unavailable"))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    private RestaurantData restaurant(String slug) {
        return new RestaurantData(
                UUID.randomUUID(), slug, "Restaurant", null, "vietnamese", "Vietnamese",
                "ho-chi-minh-city", "District 1", "District 1", "Address", "150K - 350K");
    }

    private RestaurantSummaryResponse summary(String slug) {
        return new RestaurantSummaryResponse(
                slug, "Restaurant", "ho-chi-minh-city", "District 1", "Vietnamese", "vietnamese",
                4.5, "#ffffff", null, "Restaurant", "Availability unavailable", null,
                "under300", List.of());
    }
}
