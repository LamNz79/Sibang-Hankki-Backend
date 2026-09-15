package com.sibang.hankki;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RestaurantController.class)
@Import(RestaurantAvailabilityService.class)
class RestaurantAvailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsRestaurantAvailability() throws Exception {
        mockMvc.perform(get("/api/restaurants/anan-saigon/availability")
                .param("date", LocalDate.now().toString())
                .param("partySize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantSlug").value("anan-saigon"))
                .andExpect(jsonPath("$.date").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.partySize").value(2))
                .andExpect(jsonPath("$.slots").isArray());
    }

    @Test
    void rejectsInvalidDate() throws Exception {
        mockMvc.perform(get("/api/restaurants/anan-saigon/availability")
                .param("date", "15-09-2026")
                .param("partySize", "2"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnsupportedPartySize() throws Exception {
        mockMvc.perform(get("/api/restaurants/anan-saigon/availability")
                .param("date", "2026-09-15")
                .param("partySize", "3"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForUnknownRestaurant() throws Exception {
        mockMvc.perform(get("/api/restaurants/unknown/availability")
                .param("date", "2026-09-15")
                .param("partySize", "2"))
                .andExpect(status().isNotFound());
    }
}
