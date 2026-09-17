package com.sibang.hankki.restaurant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RestaurantController.class)
@Import(RestaurantAvailabilityService.class)
class RestaurantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsRestaurantsWithoutSlots() throws Exception {
        mockMvc.perform(get("/api/restaurants"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].slug").value("anan-saigon"))
                .andExpect(jsonPath("$[0].slotMatrix").doesNotExist());
    }

    @Test
    void returnsRestaurantDetailsIncludingSlots() throws Exception {
        mockMvc.perform(get("/api/restaurants/anan-saigon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Anan Saigon"))
                .andExpect(jsonPath("$.slotMatrix").isMap());
    }

    @Test
    void returnsNotFoundForUnknownRestaurant() throws Exception {
        mockMvc.perform(get("/api/restaurants/unknown"))
                .andExpect(status().isNotFound());
    }
}
