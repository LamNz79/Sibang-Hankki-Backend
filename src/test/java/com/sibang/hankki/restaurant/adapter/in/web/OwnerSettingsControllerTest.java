package com.sibang.hankki.restaurant.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sibang.hankki.auth.OwnerSessionUserDetailsService;
import com.sibang.hankki.auth.config.SecurityConfig;
import com.sibang.hankki.restaurant.application.model.OwnerSettings;
import com.sibang.hankki.restaurant.application.port.in.OwnerSettingsUseCase;
import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;
import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import com.sibang.hankki.user.adapter.out.persistence.repository.UserAuthenticationRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(OwnerSettingsController.class)
@Import({SecurityConfig.class, OwnerSessionUserDetailsService.class})
class OwnerSettingsControllerTest {

    private static final String PASSWORD = "test-password";
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private OwnerSettingsUseCase useCase;
    @MockitoBean
    private UserAuthenticationRepository userRepository;

    @BeforeEach
    void configureUsers() {
        UserEntity owner = user("owner", "OWNER");
        UserEntity staff = user("staff", "STAFF");
        given(userRepository.findByUseridAndStatusAndDeletedAtIsNull("owner", "ACTIVE"))
                .willReturn(Optional.of(owner));
        given(userRepository.findByUseridAndStatusAndDeletedAtIsNull("staff", "ACTIVE"))
                .willReturn(Optional.of(staff));
    }

    @Test
    void ownerReadsOnlyItsSessionRestaurantSettings() throws Exception {
        given(useCase.get(RESTAURANT_ID)).willReturn(settings());

        mockMvc.perform(get("/api/owner/settings").session(login("owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(RESTAURANT_ID.toString()))
                .andExpect(jsonPath("$.name").value("The Royal Pavilion"))
                .andExpect(jsonPath("$.confirmationMode").value("AUTO"));

        verify(useCase).get(RESTAURANT_ID);
    }

    @Test
    void updateRequiresOwnerRoleAndCsrf() throws Exception {
        String request = requestJson();

        mockMvc.perform(put("/api/owner/settings")
                        .session(login("staff"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/owner/settings")
                        .session(login("owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerUpdatesItsSessionRestaurantSettings() throws Exception {
        given(useCase.update(
                org.mockito.ArgumentMatchers.eq(RESTAURANT_ID),
                org.mockito.ArgumentMatchers.any())).willReturn(settings());

        mockMvc.perform(put("/api/owner/settings")
                        .session(login("owner"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestCapacity").value(48));

        verify(useCase).update(
                org.mockito.ArgumentMatchers.eq(RESTAURANT_ID),
                org.mockito.ArgumentMatchers.argThat(update -> update.name().equals("The Royal Pavilion")));
    }

    private MockHttpSession login(String userid) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("userid", userid)
                        .param("password", PASSWORD))
                .andExpect(status().isNoContent())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private UserEntity user(String userid, String role) {
        UserEntity user = org.mockito.Mockito.mock(UserEntity.class);
        given(user.getId()).willReturn(UUID.randomUUID());
        given(user.getUserid()).willReturn(userid);
        given(user.getPasswordHash()).willReturn(passwordEncoder.encode(PASSWORD));
        given(user.getRole()).willReturn(role);
        given(user.getRestaurantId()).willReturn(RESTAURANT_ID);
        return user;
    }

    private OwnerSettings settings() {
        return new OwnerSettings(
                RESTAURANT_ID, "royal-pavilion", "The Royal Pavilion", null, "Chinese",
                "ho-chi-minh-city", "District 1", "District 1", null, null, null,
                "Asia/Ho_Chi_Minh", "$$$", 48, 30, 90, ConfirmationMode.AUTO,
                null, 30, 1, 6, 10, 120);
    }

    private String requestJson() {
        return """
                {
                  "name": "The Royal Pavilion",
                  "guestCapacity": 48,
                  "bookingIntervalMinutes": 30,
                  "diningDurationMinutes": 90,
                  "confirmationMode": "AUTO",
                  "bookingWindowDays": 30,
                  "minimumPartySize": 1,
                  "maximumOnlinePartySize": 6,
                  "largePartyThreshold": 10,
                  "customerCancellationCutoffMinutes": 120
                }
                """;
    }
}
