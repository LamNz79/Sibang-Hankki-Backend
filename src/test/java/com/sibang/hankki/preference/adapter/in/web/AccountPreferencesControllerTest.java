package com.sibang.hankki.preference.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sibang.hankki.auth.OwnerSessionUserDetailsService;
import com.sibang.hankki.auth.config.SecurityConfig;
import com.sibang.hankki.preference.application.exception.InvalidAccountPreferencesException;
import com.sibang.hankki.preference.application.port.in.AccountPreferencesUseCase;
import com.sibang.hankki.preference.application.port.in.AccountPreferencesUseCase.Preferences;
import com.sibang.hankki.preference.application.port.in.AccountPreferencesUseCase.UpdatePreferencesCommand;
import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import com.sibang.hankki.user.adapter.out.persistence.repository.UserAuthenticationRepository;
import java.util.Map;
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

@WebMvcTest(AccountPreferencesController.class)
@Import({SecurityConfig.class, OwnerSessionUserDetailsService.class, AccountPreferencesExceptionHandler.class})
class AccountPreferencesControllerTest {

    private static final String PASSWORD = "test-password";
    private static final Map<String, UUID> USER_IDS = Map.of(
            "customer", UUID.fromString("90000000-0000-0000-0000-000000000001"),
            "owner", UUID.fromString("90000000-0000-0000-0000-000000000002"),
            "staff", UUID.fromString("90000000-0000-0000-0000-000000000003"),
            "admin", UUID.fromString("90000000-0000-0000-0000-000000000004"));

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private AccountPreferencesUseCase useCase;
    @MockitoBean
    private UserAuthenticationRepository userRepository;

    @BeforeEach
    void configureUsers() {
        configureUser("customer", "CUSTOMER");
        configureUser("owner", "OWNER");
        configureUser("staff", "STAFF");
        configureUser("admin", "ADMIN");
    }

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/account/preferences"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(useCase);
    }

    @Test
    void everyAuthenticatedRoleReadsOnlyItsOwnPreferences() throws Exception {
        for (String userid : USER_IDS.keySet()) {
            UUID userId = USER_IDS.get(userid);
            given(useCase.get(userId)).willReturn(defaults());

            mockMvc.perform(get("/api/account/preferences").session(login(userid)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.themeColor").value("#B84A2F"))
                    .andExpect(jsonPath("$.displayDensity").value("COMFORTABLE"))
                    .andExpect(jsonPath("$.tablePageSize").value(20))
                    .andExpect(jsonPath("$.dateFormat").value("DD_MM_YYYY"))
                    .andExpect(jsonPath("$.locale").value("vi"));

            verify(useCase).get(userId);
        }
    }

    @Test
    void updateRequiresCsrfAndUsesAuthenticatedUserId() throws Exception {
        MockHttpSession session = login("customer");
        String body = validJson();

        mockMvc.perform(put("/api/account/preferences")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        UUID userId = USER_IDS.get("customer");
        given(useCase.update(userId, new UpdatePreferencesCommand(
                        "#b84a2f", "COMPACT", 50, "MM_DD_YYYY", "ko")))
                .willReturn(new Preferences("#B84A2F", "COMPACT", 50, "MM_DD_YYYY", "ko"));
        mockMvc.perform(put("/api/account/preferences")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.themeColor").value("#B84A2F"))
                .andExpect(jsonPath("$.tablePageSize").value(50))
                .andExpect(jsonPath("$.locale").value("ko"));

        verify(useCase).update(userId, new UpdatePreferencesCommand(
                "#b84a2f", "COMPACT", 50, "MM_DD_YYYY", "ko"));
    }

    @Test
    void invalidRequestReturnsBadRequest() throws Exception {
        UUID userId = USER_IDS.get("customer");
        given(useCase.update(org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.any()))
                .willThrow(new InvalidAccountPreferencesException("invalid"));

        mockMvc.perform(put("/api/account/preferences")
                        .with(csrf())
                        .session(login("customer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void deleteRequiresCsrfAndRemainsSuccessfulWhenRepeated() throws Exception {
        MockHttpSession session = login("admin");

        mockMvc.perform(delete("/api/account/preferences").session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/account/preferences").with(csrf()).session(session))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/account/preferences").with(csrf()).session(session))
                .andExpect(status().isNoContent());

        verify(useCase, org.mockito.Mockito.times(2)).delete(USER_IDS.get("admin"));
    }

    private void configureUser(String userid, String role) {
        UserEntity user = org.mockito.Mockito.mock(UserEntity.class);
        given(user.getId()).willReturn(USER_IDS.get(userid));
        given(user.getUserid()).willReturn(userid);
        given(user.getPasswordHash()).willReturn(passwordEncoder.encode(PASSWORD));
        given(user.getRole()).willReturn(role);
        given(userRepository.findByUseridAndStatusAndDeletedAtIsNull(userid, "ACTIVE"))
                .willReturn(Optional.of(user));
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

    private Preferences defaults() {
        return new Preferences("#B84A2F", "COMFORTABLE", 20, "DD_MM_YYYY", "vi");
    }

    private String validJson() {
        return """
                {
                  "themeColor": "#b84a2f",
                  "displayDensity": "COMPACT",
                  "tablePageSize": 50,
                  "dateFormat": "MM_DD_YYYY",
                  "locale": "ko",
                  "userId": "90000000-0000-0000-0000-000000000099"
                }
                """;
    }
}
