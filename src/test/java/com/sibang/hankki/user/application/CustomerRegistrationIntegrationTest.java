package com.sibang.hankki.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import com.sibang.hankki.user.adapter.out.persistence.repository.UserAuthenticationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class CustomerRegistrationIntegrationTest {

    private static final String USERID = "registration-it-customer";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAuthenticationRepository repository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registersCustomerWithoutStartingASession() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(USERID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userid").value(USERID))
                .andExpect(jsonPath("$.email").value("customer@example.com"))
                .andExpect(jsonPath("$.name").value("Customer Name"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        UserEntity user = repository.findByUserid(USERID).orElseThrow();
        assertTrue(passwordEncoder.matches("Customer@2026", user.getPasswordHash()));
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateUseridReturnsConflict() throws Exception {
        repository.saveAndFlush(new UserEntity(
                USERID, "existing@example.com", passwordEncoder.encode("Existing@2026"), "Existing"));

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(USERID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void invalidInputReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("ab")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void clientFieldsCannotElevateCustomerPrivileges() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userid": "registration-it-privilege",
                                  "email": "customer@example.com",
                                  "name": "Customer Name",
                                  "password": "Customer@2026",
                                  "role": "ADMIN",
                                  "status": "SUSPENDED",
                                  "restaurantId": "00000000-0000-0000-0000-000000000001"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        UserEntity user = repository.findByUserid("registration-it-privilege").orElseThrow();
        assertEquals("CUSTOMER", user.getRole());
        assertEquals("ACTIVE", user.getStatus());
        assertNull(user.getRestaurantId());
    }

    private String validRequest(String userid) {
        return """
                {
                  "userid": "%s",
                  "email": "customer@example.com",
                  "name": "Customer Name",
                  "password": "Customer@2026"
                }
                """.formatted(userid);
    }
}
