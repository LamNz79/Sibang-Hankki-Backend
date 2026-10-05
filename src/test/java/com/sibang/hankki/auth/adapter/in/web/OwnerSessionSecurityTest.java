package com.sibang.hankki.auth.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sibang.hankki.auth.OwnerSessionUserDetailsService;
import com.sibang.hankki.auth.config.SecurityConfig;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest({AuthController.class, OwnerSessionSecurityTest.OwnerProbeController.class})
@Import({SecurityConfig.class, OwnerSessionUserDetailsService.class})
class OwnerSessionSecurityTest {

    private static final String PASSWORD = "test-password";
    private static final UUID OWNER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserAuthenticationRepository repository;

    @BeforeEach
    void configureUsers() {
        UserEntity owner = user(OWNER_ID, "owner", "OWNER", RESTAURANT_ID);
        UserEntity customer = user(UUID.randomUUID(), "customer", "CUSTOMER", null);
        given(repository.findByUseridAndStatusAndDeletedAtIsNull("owner", "ACTIVE"))
                .willReturn(Optional.of(owner));
        given(repository.findByUseridAndStatusAndDeletedAtIsNull("customer", "ACTIVE"))
                .willReturn(Optional.of(customer));
    }

    @Test
    void issuesCsrfToken() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerLoginCreatesSessionAndReturnsIdentity() throws Exception {
        MockHttpSession session = login("owner");

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(OWNER_ID.toString()))
                .andExpect(jsonPath("$.userid").value("owner"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.restaurantId").value(RESTAURANT_ID.toString()));
    }

    @Test
    void customerCannotAccessOwnerApi() throws Exception {
        mockMvc.perform(get("/api/owner/probe").session(login("customer")))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("userid", "owner")
                        .param("password", PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidCredentialsReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("userid", "owner")
                        .param("password", "wrong"))
                .andExpect(status().isUnauthorized());
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

    private UserEntity user(UUID id, String userid, String role, UUID restaurantId) {
        UserEntity user = org.mockito.Mockito.mock(UserEntity.class);
        given(user.getId()).willReturn(id);
        given(user.getUserid()).willReturn(userid);
        given(user.getPasswordHash()).willReturn(passwordEncoder.encode(PASSWORD));
        given(user.getRole()).willReturn(role);
        given(user.getRestaurantId()).willReturn(restaurantId);
        return user;
    }

    @RestController
    @RequestMapping("/api/owner")
    static class OwnerProbeController {

        @GetMapping("/probe")
        String probe() {
            return "ok";
        }
    }
}
