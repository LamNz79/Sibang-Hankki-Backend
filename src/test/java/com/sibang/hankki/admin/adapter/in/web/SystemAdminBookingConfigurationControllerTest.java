package com.sibang.hankki.admin.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.port.in.SystemAdminBookingConfigurationUseCase;
import com.sibang.hankki.auth.config.SecurityConfig;
import com.sibang.hankki.restaurant.application.exception.BookingSettingsNotConfiguredException;
import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.model.BookingSettingsUpdate;
import com.sibang.hankki.restaurant.application.model.OwnerSettings;
import com.sibang.hankki.restaurant.application.port.in.OwnerBusinessHoursUseCase;
import com.sibang.hankki.restaurant.application.port.in.OwnerSlotRegenerationUseCase;
import com.sibang.hankki.restaurant.application.port.out.RestaurantBusinessHourData;
import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SystemAdminBookingConfigurationController.class)
@Import(SecurityConfig.class)
class SystemAdminBookingConfigurationControllerTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemAdminBookingConfigurationUseCase useCase;

    @Test
    void onlyAdminCanReadConfiguration() throws Exception {
        mockMvc.perform(get(path("/booking-settings")))
                .andExpect(status().isUnauthorized());

        for (String role : List.of("CUSTOMER", "OWNER", "STAFF")) {
            mockMvc.perform(get(path("/booking-settings")).with(user(role.toLowerCase()).roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminReadsBookingSettingsAndBusinessHours() throws Exception {
        given(useCase.getBookingSettings(RESTAURANT_ID)).willReturn(settings(null));
        given(useCase.getBusinessHours(RESTAURANT_ID)).willReturn(List.of(hour(1, "11:30", "14:00")));

        mockMvc.perform(get(path("/booking-settings")).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmationMode").value("HYBRID"))
                .andExpect(jsonPath("$.slotIntervalMinutes").value(60))
                .andExpect(jsonPath("$.cancellationCutoffMinutes").value(120))
                .andExpect(jsonPath("$.noShowGraceMinutes").doesNotExist());

        mockMvc.perform(get(path("/business-hours")).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$[0].opensAt").value("11:30:00"));
    }

    @Test
    void adminUpdatesExplicitNullAndZeroValues() throws Exception {
        given(useCase.updateBookingSettings(eq(RESTAURANT_ID), any(BookingSettingsUpdate.class)))
                .willReturn(settings(0));

        mockMvc.perform(put(path("/booking-settings"))
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settingsRequest("null", "0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manualConfirmationMinPartySize").value(7))
                .andExpect(jsonPath("$.cancellationCutoffMinutes").value(120))
                .andExpect(jsonPath("$.noShowGraceMinutes").value(0));

        verify(useCase).updateBookingSettings(
                eq(RESTAURANT_ID),
                org.mockito.ArgumentMatchers.argThat(update -> update.noShowGraceMinutes() == 0
                        && update.customerCancellationCutoffMinutes() == null));
    }

    @Test
    void adminUpdatesHoursAndRegeneratesOnlySelectedRestaurant() throws Exception {
        List<RestaurantBusinessHourData> hours = List.of(hour(2, "17:00", "22:00"));
        given(useCase.updateBusinessHours(eq(RESTAURANT_ID), any()))
                .willReturn(new OwnerBusinessHoursUseCase.UpdateResult(
                        hours, new OwnerSlotRegenerationUseCase.Result(4, 8)));
        given(useCase.regenerateSlots(RESTAURANT_ID))
                .willReturn(new OwnerSlotRegenerationUseCase.Result(3, 9));

        mockMvc.perform(put(path("/business-hours"))
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hours":[{"dayOfWeek":2,"opensAt":"17:00","closesAt":"22:00"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hours[0].dayOfWeek").value(2))
                .andExpect(jsonPath("$.deletedSlots").value(4))
                .andExpect(jsonPath("$.generatedSlots").value(8));

        mockMvc.perform(post(path("/booking-slots/regenerate"))
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedSlots").value(3))
                .andExpect(jsonPath("$.generatedSlots").value(9));

        verify(useCase).regenerateSlots(RESTAURANT_ID);
    }

    @Test
    void writesRequireCsrf() throws Exception {
        mockMvc.perform(put(path("/booking-settings"))
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settingsRequest("7", "15")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(path("/business-hours"))
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hours\":[]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path("/booking-slots/regenerate"))
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidPayloadAndUnknownRestaurantUseAdminErrors() throws Exception {
        given(useCase.updateBookingSettings(eq(RESTAURANT_ID), any()))
                .willThrow(new InvalidBookingRequestException("invalid"));
        given(useCase.updateBusinessHours(eq(RESTAURANT_ID), any()))
                .willThrow(new InvalidBookingRequestException("business hours must not overlap"));
        given(useCase.getBusinessHours(RESTAURANT_ID))
                .willThrow(new AdminResourceNotFoundException("Restaurant not found"));

        mockMvc.perform(put(path("/booking-settings"))
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settingsRequest("7", "-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(put(path("/business-hours"))
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hours":[
                                  {"dayOfWeek":1,"opensAt":"11:00","closesAt":"15:00"},
                                  {"dayOfWeek":1,"opensAt":"14:00","closesAt":"18:00"}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(get(path("/business-hours")).with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void missingBookingSettingsReturnsConflict() throws Exception {
        given(useCase.getBookingSettings(RESTAURANT_ID))
                .willThrow(new BookingSettingsNotConfiguredException());

        mockMvc.perform(get(path("/booking-settings")).with(user("admin").roles("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    private String path(String suffix) {
        return "/api/admin/restaurants/" + RESTAURANT_ID + suffix;
    }

    private RestaurantBusinessHourData hour(int day, String opensAt, String closesAt) {
        return new RestaurantBusinessHourData(
                RESTAURANT_ID, (short) day, LocalTime.parse(opensAt), LocalTime.parse(closesAt));
    }

    private OwnerSettings settings(Integer noShowGraceMinutes) {
        return new OwnerSettings(
                RESTAURANT_ID, "royal-pavilion", "The Royal Pavilion", null, "Chinese",
                "ho-chi-minh", null, null, null, null, null, "Asia/Ho_Chi_Minh", "$$$",
                20, 60, 90, ConfirmationMode.HYBRID, 7, 30, 1, 10, 11, 120, noShowGraceMinutes);
    }

    private String settingsRequest(String cancellationCutoff, String noShowGrace) {
        return """
                {
                  "confirmationMode": "HYBRID",
                  "manualConfirmationMinPartySize": 7,
                  "guestCapacity": 20,
                  "slotIntervalMinutes": 60,
                  "diningDurationMinutes": 90,
                  "bookingWindowDays": 30,
                  "minimumPartySize": 1,
                  "maximumOnlinePartySize": 10,
                  "largePartyThreshold": 11,
                  "cancellationCutoffMinutes": %s,
                  "noShowGraceMinutes": %s
                }
                """.formatted(cancellationCutoff, noShowGrace);
    }
}
