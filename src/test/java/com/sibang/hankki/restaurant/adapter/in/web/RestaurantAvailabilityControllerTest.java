package com.sibang.hankki.restaurant.adapter.in.web;
import com.sibang.hankki.restaurant.application.RestaurantAvailabilityService;
import com.sibang.hankki.restaurant.application.RestaurantCatalogService;
import com.sibang.hankki.restaurant.application.model.RestaurantAvailabilityResponse;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RestaurantController.class)
class RestaurantAvailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RestaurantCatalogService catalogService;

    @MockitoBean
    private RestaurantAvailabilityService availabilityService;

    @Test
    void preservesAvailabilityResponseContract() throws Exception {
        given(availabilityService.availability("anan-saigon", "2026-10-05", 2)).willReturn(
                new RestaurantAvailabilityResponse("anan-saigon", "2026-10-05", 2, List.of("11:30", "12:30"), false));

        mockMvc.perform(get("/api/restaurants/anan-saigon/availability")
                .param("date", "2026-10-05")
                .param("partySize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantSlug").value("anan-saigon"))
                .andExpect(jsonPath("$.date").value("2026-10-05"))
                .andExpect(jsonPath("$.partySize").value(2))
                .andExpect(jsonPath("$.slots[0]").value("11:30"))
                .andExpect(jsonPath("$.requiresRestaurantConfirmation").value(false));
    }

    @Test
    void returnsServiceErrorsWithoutChangingEndpointShape() throws Exception {
        given(availabilityService.availability("unknown", "2026-10-05", 2))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found"));

        mockMvc.perform(get("/api/restaurants/unknown/availability")
                .param("date", "2026-10-05")
                .param("partySize", "2"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsMissingAndNonNumericParameters() throws Exception {
        mockMvc.perform(get("/api/restaurants/anan-saigon/availability").param("partySize", "2"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/restaurants/anan-saigon/availability")
                .param("date", "2026-10-05").param("partySize", "two"))
                .andExpect(status().isBadRequest());
    }
}
