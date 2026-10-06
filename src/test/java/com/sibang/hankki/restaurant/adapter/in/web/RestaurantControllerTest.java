package com.sibang.hankki.restaurant.adapter.in.web;
import com.sibang.hankki.auth.config.SecurityConfig;
import com.sibang.hankki.restaurant.application.port.in.RestaurantAvailabilityUseCase;
import com.sibang.hankki.restaurant.application.port.in.RestaurantCatalogUseCase;
import com.sibang.hankki.restaurant.application.model.RestaurantResponse;
import com.sibang.hankki.restaurant.application.model.RestaurantSummaryResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;

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
        given(catalogService.restaurants()).willReturn(java.util.Collections.nCopies(6, summary()));

        mockMvc.perform(get("/api/restaurants"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].slug").value("anan-saigon"))
                .andExpect(jsonPath("$[0].imageUrl").value("https://example.test/anan.jpg"))
                .andExpect(jsonPath("$[0].slotMatrix").doesNotExist());
    }

    @Test
    void returnsRestaurantDetailsIncludingSlots() throws Exception {
        given(catalogService.restaurant("anan-saigon")).willReturn(Optional.of(restaurant()));

        mockMvc.perform(get("/api/restaurants/anan-saigon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Anan Saigon"))
                .andExpect(jsonPath("$.imageUrl").value("https://example.test/anan.jpg"))
                .andExpect(jsonPath("$.slotMatrix").isMap());
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
                "Vietnamese contemporary", "vietnamese", 4.7, 139, "150K - 350K", "#f6ede4",
                "https://example.test/anan.jpg", "Anan Saigon dining room",
                "11:30 - 22:00", "District 1", "Available today from 18:30", "18:30", "under300",
                List.of("michelin", "special_deal", "available"), List.of("Michelin", "Special deal", "Date night"),
                5, "summary", Map.of("2026-09-15", Map.of("2", List.of("18:30"))));
    }
}
