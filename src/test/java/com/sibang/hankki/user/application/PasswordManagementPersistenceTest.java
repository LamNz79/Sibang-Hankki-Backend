package com.sibang.hankki.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import com.sibang.hankki.user.adapter.out.persistence.repository.UserAuthenticationRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class PasswordManagementPersistenceTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String OLD_PASSWORD = "OldPassword@2026!";
    private static final String NEW_PASSWORD = "NewPassword@2026!";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAuthenticationRepository repository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void selfServiceChangesBcryptPasswordAndInvalidatesCurrentAndExistingSessions() throws Exception {
        UserEntity customer = saveUser("self-password", "CUSTOMER", null, OLD_PASSWORD);
        MockHttpSession callerSession = login(customer.getUserid(), OLD_PASSWORD);
        MockHttpSession existingSession = login(customer.getUserid(), OLD_PASSWORD);

        mockMvc.perform(patch("/api/auth/password")
                        .session(callerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(OLD_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(callerSession.isInvalid()).isTrue();
        assertThat(passwordEncoder.matches(
                NEW_PASSWORD, repository.findById(customer.getId()).orElseThrow().getPasswordHash())).isTrue();
        loginFails(customer.getUserid(), OLD_PASSWORD);
        login(customer.getUserid(), NEW_PASSWORD);
        mockMvc.perform(get("/api/auth/me").session(existingSession))
                .andExpect(status().isUnauthorized());
        assertThat(existingSession.isInvalid()).isTrue();
    }

    @Test
    void adminResetsSupportedRolesAndCannotResetAnotherAdmin() throws Exception {
        UserEntity admin = saveUser("password-admin", "ADMIN", null, OLD_PASSWORD);
        UserEntity customer = saveUser("password-customer", "CUSTOMER", null, OLD_PASSWORD);
        UserEntity owner = saveUser("password-owner", "OWNER", RESTAURANT_ID, OLD_PASSWORD);
        UserEntity staff = saveUser("password-staff", "STAFF", RESTAURANT_ID, OLD_PASSWORD);
        UserEntity otherAdmin = saveUser("password-other-admin", "ADMIN", null, OLD_PASSWORD);
        MockHttpSession adminSession = login(admin.getUserid(), OLD_PASSWORD);
        MockHttpSession customerSession = login(customer.getUserid(), OLD_PASSWORD);

        for (UserEntity target : java.util.List.of(customer, owner, staff)) {
            mockMvc.perform(patch("/api/admin/users/{id}/password", target.getId())
                            .session(adminSession).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
            assertThat(passwordEncoder.matches(
                    NEW_PASSWORD, repository.findById(target.getId()).orElseThrow().getPasswordHash())).isTrue();
        }

        mockMvc.perform(get("/api/auth/me").session(customerSession))
                .andExpect(status().isUnauthorized());
        assertThat(customerSession.isInvalid()).isTrue();
        mockMvc.perform(patch("/api/admin/users/{id}/password", otherAdmin.getId())
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        assertThat(passwordEncoder.matches(
                OLD_PASSWORD, repository.findById(otherAdmin.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    private UserEntity saveUser(String prefix, String role, UUID restaurantId, String password) {
        String userid = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        return repository.saveAndFlush(new UserEntity(
                userid, passwordEncoder.encode(password), prefix, role, restaurantId));
    }

    private MockHttpSession login(String userid, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("userid", userid)
                        .param("password", password))
                .andExpect(status().isNoContent())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void loginFails(String userid, String password) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("userid", userid)
                        .param("password", password))
                .andExpect(status().isUnauthorized());
    }

    private String passwordBody(String currentPassword, String newPassword) {
        return "{\"currentPassword\":\"" + currentPassword
                + "\",\"newPassword\":\"" + newPassword + "\"}";
    }
}
