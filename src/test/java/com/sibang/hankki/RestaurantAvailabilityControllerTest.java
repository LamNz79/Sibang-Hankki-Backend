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
                .andExpect(jsonPath("$.slots").isArray())
                .andExpect(jsonPath("$.requiresRestaurantConfirmation").value(false));
    }

    @Test
    void rejectsInvalidDate() throws Exception {
        mockMvc.perform(get("/api/restaurants/anan-saigon/availability")
                .param("date", "15-09-2026")
                .param("partySize", "2"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsPartySizeThreeWithoutRounding() throws Exception {
        mockMvc.perform(get("/api/restaurants/anan-saigon/availability")
                .param("date", "2026-09-15")
                .param("partySize", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partySize").value(3))
                .andExpect(jsonPath("$.requiresRestaurantConfirmation").value(false));
    }

    @Test
    void returnsEmptySlotsForGroupsWithoutMockCapacity() throws Exception {
        for (int partySize : new int[] {7, 10}) {
            mockMvc.perform(get("/api/restaurants/anan-saigon/availability")
                    .param("date", "2026-09-15")
                    .param("partySize", String.valueOf(partySize)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slots").isEmpty())
                    .andExpect(jsonPath("$.requiresRestaurantConfirmation").value(false));
        }

        mockMvc.perform(get("/api/restaurants/anan-saigon/availability")
                .param("date", "2026-09-15")
                .param("partySize", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots").isEmpty())
                .andExpect(jsonPath("$.requiresRestaurantConfirmation").value(true));
    }

    @Test
    void rejectsMissingDateAndInvalidPartySizes() throws Exception {
        mockMvc.perform(get("/api/restaurants/anan-saigon/availability")
                .param("partySize", "2"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/restaurants/anan-saigon/availability")
                .param("date", "2026-09-15"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/restaurants/anan-saigon/availability")
                .param("date", "2026-09-15")
                .param("partySize", "two"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/restaurants/anan-saigon/availability")
                .param("date", "2026-09-15")
                .param("partySize", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/restaurants/anan-saigon/availability")
                .param("date", "2026-09-15")
                .param("partySize", "-1"))
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
