package com.sibang.hankki.restaurant.adapter.in.web;

import com.sibang.hankki.auth.config.SecurityConfig;
import com.sibang.hankki.restaurant.application.port.in.RestaurantAvailabilityUseCase;
import com.sibang.hankki.restaurant.application.port.in.RestaurantCatalogUseCase;
import com.sibang.hankki.restaurant.application.exception.BookingSettingsNotConfiguredException;
import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;
import com.sibang.hankki.restaurant.application.model.RestaurantAvailabilityResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RestaurantController.class)
@Import(SecurityConfig.class)
class RestaurantAvailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RestaurantCatalogUseCase catalogService;

    @MockitoBean
    private RestaurantAvailabilityUseCase availabilityService;

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
    void mapsInvalidBookingRequestToBadRequest() throws Exception {
        given(availabilityService.availability("anan-saigon", "invalid", 2))
                .willThrow(new InvalidBookingRequestException("Invalid date"));

        mockMvc.perform(get("/api/restaurants/anan-saigon/availability")
                .param("date", "invalid")
                .param("partySize", "2"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsRestaurantNotFoundToNotFound() throws Exception {
        given(availabilityService.availability("unknown", "2026-10-05", 2))
                .willThrow(new RestaurantNotFoundException());

        mockMvc.perform(get("/api/restaurants/unknown/availability")
                .param("date", "2026-10-05")
                .param("partySize", "2"))
                .andExpect(status().isNotFound());
    }

    @Test
    void mapsMissingBookingSettingsToConflict() throws Exception {
        given(availabilityService.availability("anan-saigon", "2026-10-05", 2))
                .willThrow(new BookingSettingsNotConfiguredException());

        mockMvc.perform(get("/api/restaurants/anan-saigon/availability")
                .param("date", "2026-10-05")
                .param("partySize", "2"))
                .andExpect(status().isConflict());
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
