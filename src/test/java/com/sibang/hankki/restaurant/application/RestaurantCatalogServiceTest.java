package com.sibang.hankki.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.model.OwnerMenu;
import com.sibang.hankki.restaurant.application.model.RestaurantResponse;
import com.sibang.hankki.restaurant.application.port.out.BookingSettings;
import com.sibang.hankki.restaurant.application.port.out.BookingSettingsPort;
import com.sibang.hankki.restaurant.application.port.out.OwnerMenuPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantData;
import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RestaurantCatalogServiceTest {

    private final RestaurantCatalogPort catalogPort =
            org.mockito.Mockito.mock(RestaurantCatalogPort.class);
    private final OwnerMenuPort menuPort = org.mockito.Mockito.mock(OwnerMenuPort.class);
    private final BookingSettingsPort bookingSettingsPort = org.mockito.Mockito.mock(BookingSettingsPort.class);
    private final RestaurantCatalogService service =
            new RestaurantCatalogService(catalogPort, menuPort, bookingSettingsPort);

    @Test
    void listRemainsLightweightAndDoesNotLoadMenu() {
        RestaurantData restaurant = restaurant();
        given(catalogPort.findAllActiveRestaurants(null, null)).willReturn(List.of(restaurant));

        var response = service.restaurants(null, null).get(0);

        assertThat(response.rating()).isZero();
        assertThat(response.heroAccent()).isEqualTo("#f3f4f6");
        assertThat(response.availableText()).isNull();
        assertThat(response.availableFrom()).isNull();
        assertThat(response.benefits()).isEmpty();
        verify(menuPort, never()).findAll(restaurant.id());
        verify(bookingSettingsPort, never()).findByRestaurantId(restaurant.id());
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
        given(bookingSettingsPort.findByRestaurantId(restaurant.id())).willReturn(Optional.of(new BookingSettings(
                restaurant.id(), 20, 60, 90, ConfirmationMode.HYBRID, 7,
                45, 2, 10, 11)));

        var response = service.restaurant(restaurant.slug()).orElseThrow();

        assertThat(response.rating()).isZero();
        assertThat(response.ratingCount()).isZero();
        assertThat(response.availableText()).isNull();
        assertThat(response.availableFrom()).isNull();
        assertThat(response.slotMatrix()).isEmpty();
        assertThat(response.bookingPolicy()).isEqualTo(new RestaurantResponse.BookingPolicy(45, 2, 10, 11));
        assertThat(response.menuCategories()).hasSize(1);
        assertThat(response.menuCategories().get(0).name()).isEqualTo("Main dishes");
        assertThat(response.menuCategories().get(0).items())
                .extracting(item -> item.name())
                .containsExactly("Grilled beef");
        verify(menuPort).findAll(restaurant.id());
    }

    @Test
    void detailKeepsMissingBookingPolicyNullable() {
        RestaurantData restaurant = restaurant();
        given(catalogPort.findActiveRestaurantBySlug(restaurant.slug())).willReturn(Optional.of(restaurant));
        given(menuPort.findAll(restaurant.id())).willReturn(new OwnerMenu(List.of(), List.of()));
        given(bookingSettingsPort.findByRestaurantId(restaurant.id())).willReturn(Optional.empty());

        assertThat(service.restaurant(restaurant.slug()).orElseThrow().bookingPolicy()).isNull();
    }

    @Test
    void unknownRestaurantDoesNotLoadMenu() {
        given(catalogPort.findActiveRestaurantBySlug("unknown")).willReturn(Optional.empty());

        assertThat(service.restaurant("unknown")).isEmpty();
        verify(menuPort, never()).findAll(org.mockito.ArgumentMatchers.any());
        verify(bookingSettingsPort, never()).findByRestaurantId(org.mockito.ArgumentMatchers.any());
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
