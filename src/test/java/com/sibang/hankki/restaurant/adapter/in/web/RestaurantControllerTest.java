package com.sibang.hankki.restaurant.adapter.in.web;
import com.sibang.hankki.auth.config.SecurityConfig;
import com.sibang.hankki.restaurant.application.port.in.RestaurantAvailabilityUseCase;
import com.sibang.hankki.restaurant.application.port.in.RestaurantCatalogUseCase;
import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.model.RestaurantResponse;
import com.sibang.hankki.restaurant.application.model.RestaurantSummaryResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RestaurantController.class)
@Import(SecurityConfig.class)
class RestaurantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RestaurantCatalogUseCase catalogService;

    @MockitoBean
    private RestaurantAvailabilityUseCase availabilityService;

    @Test
    void listsRestaurantsWithoutSlots() throws Exception {
        given(catalogService.restaurants(null, null)).willReturn(java.util.Collections.nCopies(6, summary()));

        mockMvc.perform(get("/api/restaurants"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].slug").value("anan-saigon"))
                .andExpect(jsonPath("$[0].imageUrl").value("https://example.test/anan.jpg"))
                .andExpect(jsonPath("$[0].slotMatrix").doesNotExist())
                .andExpect(jsonPath("$[0].menuCategories").doesNotExist())
                .andExpect(jsonPath("$[0].bookingPolicy").doesNotExist());

        verify(catalogService).restaurants(null, null);
    }

    @Test
    void forwardsProvinceFilter() throws Exception {
        given(catalogService.restaurants(79, null)).willReturn(List.of(summary()));

        mockMvc.perform(get("/api/restaurants").param("provinceCode", "79"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(catalogService).restaurants(79, null);
    }

    @Test
    void forwardsProvinceAndWardFilters() throws Exception {
        given(catalogService.restaurants(79, 27169)).willReturn(List.of(summary()));

        mockMvc.perform(get("/api/restaurants")
                        .param("provinceCode", "79")
                        .param("wardCode", "27169"))
                .andExpect(status().isOk());

        verify(catalogService).restaurants(79, 27169);
    }

    @Test
    void rejectsNonPositiveLocationCodes() throws Exception {
        given(catalogService.restaurants(0, null))
                .willThrow(new InvalidBookingRequestException("provinceCode must be positive"));
        given(catalogService.restaurants(null, -1))
                .willThrow(new InvalidBookingRequestException("wardCode must be positive"));

        mockMvc.perform(get("/api/restaurants").param("provinceCode", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/restaurants").param("wardCode", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsRestaurantDetailsWithEmptyLegacySlotMatrix() throws Exception {
        given(catalogService.restaurant("anan-saigon")).willReturn(Optional.of(restaurant()));

        mockMvc.perform(get("/api/restaurants/anan-saigon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Anan Saigon"))
                .andExpect(jsonPath("$.imageUrl").value("https://example.test/anan.jpg"))
                .andExpect(jsonPath("$.galleryImages[0].imageUrl").value("https://example.test/anan.jpg"))
                .andExpect(jsonPath("$.menuCategories[0].name").value("Main dishes"))
                .andExpect(jsonPath("$.menuCategories[0].items[0].name").value("Grilled beef"))
                .andExpect(jsonPath("$.menuCategories[0].items[0].description").value("With vegetables"))
                .andExpect(jsonPath("$.menuCategories[0].items[0].price").value(250000))
                .andExpect(jsonPath("$.menuCategories[0].items[0].currency").value("VND"))
                .andExpect(jsonPath("$.menuCategories[0].items[0].imageUrl")
                        .value("https://example.test/beef.jpg"))
                .andExpect(jsonPath("$.slotMatrix").isEmpty())
                .andExpect(jsonPath("$.rating").value(0))
                .andExpect(jsonPath("$.ratingCount").value(0))
                .andExpect(jsonPath("$.availableText").doesNotExist())
                .andExpect(jsonPath("$.availableFrom").doesNotExist())
                .andExpect(jsonPath("$.bookingPolicy.bookingWindowDays").value(30))
                .andExpect(jsonPath("$.bookingPolicy.minimumPartySize").value(2))
                .andExpect(jsonPath("$.bookingPolicy.maximumOnlinePartySize").value(10))
                .andExpect(jsonPath("$.bookingPolicy.largePartyThreshold").value(11))
                .andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"));
    }

    @Test
    void returnsNotFoundForUnknownRestaurant() throws Exception {
        given(catalogService.restaurant("unknown")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/restaurants/unknown"))
                .andExpect(status().isNotFound());
    }

    private RestaurantSummaryResponse summary() {
        return RestaurantSummaryResponse.from(restaurant());
    }

    private RestaurantResponse restaurant() {
        return new RestaurantResponse(
                "anan-saigon", "Anan Saigon", "ho-chi-minh-city", "District 1", "District 1",
                "Vietnamese contemporary", "vietnamese", 0, 0, "150K - 350K", "#f3f4f6",
                "https://example.test/anan.jpg", "Anan Saigon dining room",
                "11:30 - 22:00", "District 1", null, null, "under300",
                List.of("michelin", "special_deal"), List.of("Michelin", "Special deal", "Date night"),
                5, List.of(new RestaurantResponse.GalleryImage(
                        "https://example.test/anan.jpg", "Anan Saigon dining room")),
                "summary", Map.of(),
                List.of(new RestaurantResponse.MenuCategory(
                        UUID.fromString("20000000-0000-0000-0000-000000000001"),
                        "Main dishes", 0, List.of(new RestaurantResponse.MenuItem(
                                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                                "Grilled beef", "With vegetables", new BigDecimal("250000"),
                                "VND", "https://example.test/beef.jpg")))),
                "Asia/Ho_Chi_Minh",
                new RestaurantResponse.BookingPolicy(30, 2, 10, 11));
    }
}
