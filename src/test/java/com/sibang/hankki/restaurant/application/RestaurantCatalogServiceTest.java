package com.sibang.hankki.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sibang.hankki.restaurant.application.model.OwnerMenu;
import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.port.out.OwnerMenuPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantData;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RestaurantCatalogServiceTest {

    private final RestaurantCatalogPort catalogPort =
            org.mockito.Mockito.mock(RestaurantCatalogPort.class);
    private final OwnerMenuPort menuPort = org.mockito.Mockito.mock(OwnerMenuPort.class);
    private final RestaurantCatalogService service = new RestaurantCatalogService(catalogPort, menuPort);

    @Test
    void listRemainsLightweightAndDoesNotLoadMenu() {
        RestaurantData restaurant = restaurant();
        given(catalogPort.findAllActiveRestaurants(null, null)).willReturn(List.of(restaurant));

        assertThat(service.restaurants(null, null)).hasSize(1);
        verify(menuPort, never()).findAll(restaurant.id());
    }

    @Test
    void forwardsLocationFiltersAndRejectsNonPositiveCodes() {
        given(catalogPort.findAllActiveRestaurants(79, 27169)).willReturn(List.of(restaurant()));

        assertThat(service.restaurants(79, 27169)).hasSize(1);
        verify(catalogPort).findAllActiveRestaurants(79, 27169);

        assertThatThrownBy(() -> service.restaurants(0, null))
                .isInstanceOf(InvalidBookingRequestException.class);
        assertThatThrownBy(() -> service.restaurants(null, -1))
                .isInstanceOf(InvalidBookingRequestException.class);
    }

    @Test
    void detailIncludesOnlyAvailableItemsAndHidesEmptyCategories() {
        RestaurantData restaurant = restaurant();
        UUID visibleCategoryId = UUID.randomUUID();
        UUID emptyCategoryId = UUID.randomUUID();
        UUID visibleItemId = UUID.randomUUID();
        given(catalogPort.findActiveRestaurantBySlug(restaurant.slug()))
                .willReturn(Optional.of(restaurant));
        given(menuPort.findAll(restaurant.id())).willReturn(new OwnerMenu(
                List.of(
                        new OwnerMenu.Category(visibleCategoryId, "Main dishes", 0),
                        new OwnerMenu.Category(emptyCategoryId, "Desserts", 1)),
                List.of(
                        item(visibleItemId, visibleCategoryId, "Grilled beef", true),
                        item(UUID.randomUUID(), visibleCategoryId, "Unavailable beef", false),
                        item(UUID.randomUUID(), emptyCategoryId, "Unavailable cake", false))));

        var response = service.restaurant(restaurant.slug()).orElseThrow();

        assertThat(response.menuCategories()).hasSize(1);
        assertThat(response.menuCategories().get(0).name()).isEqualTo("Main dishes");
        assertThat(response.menuCategories().get(0).items())
                .extracting(item -> item.name())
                .containsExactly("Grilled beef");
        verify(menuPort).findAll(restaurant.id());
    }

    @Test
    void unknownRestaurantDoesNotLoadMenu() {
        given(catalogPort.findActiveRestaurantBySlug("unknown")).willReturn(Optional.empty());

        assertThat(service.restaurant("unknown")).isEmpty();
        verify(menuPort, never()).findAll(org.mockito.ArgumentMatchers.any());
    }

    private OwnerMenu.Item item(UUID id, UUID categoryId, String name, boolean available) {
        return new OwnerMenu.Item(
                id, categoryId, name, "Description", new BigDecimal("250000"),
                "VND", "https://example.com/item.jpg", available);
    }

    private RestaurantData restaurant() {
        return new RestaurantData(
                UUID.randomUUID(), "test-restaurant", "Test Restaurant", "Description",
                "vietnamese", "Vietnamese", "ho-chi-minh-city", "District 1", "District 1",
                "Address", "150K - 350K");
    }
}
