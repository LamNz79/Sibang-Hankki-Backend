package com.sibang.hankki.admin.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sibang.hankki.admin.application.port.in.RestaurantAssignmentUseCase;
import com.sibang.hankki.admin.application.port.in.RestaurantAssignmentUseCase.Assignment;
import com.sibang.hankki.admin.application.port.in.RestaurantAssignmentUseCase.AssignmentPage;
import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase.AdminUser;
import com.sibang.hankki.admin.application.port.in.SystemAdminUserUseCase;
import com.sibang.hankki.auth.config.SecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({SystemAdminUserController.class, RestaurantAssignmentController.class})
@Import(SecurityConfig.class)
class SystemAdminUserAccessControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID RESTAURANT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-14T03:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemAdminUserUseCase userUseCase;

    @MockitoBean
    private RestaurantAssignmentUseCase assignmentUseCase;

    @Test
    void anonymousAndWrongRolesAreBlocked() throws Exception {
        mockMvc.perform(get("/api/admin/users/{id}", USER_ID)).andExpect(status().isUnauthorized());
        for (String role : List.of("CUSTOMER", "OWNER", "STAFF")) {
            mockMvc.perform(get("/api/admin/restaurant-assignments")
                            .with(user(role.toLowerCase()).roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminReadsCreatesAndUpdatesCustomerWithoutCredentialExposure() throws Exception {
        AdminUser response = customer();
        given(userUseCase.findById(USER_ID)).willReturn(response);
        given(userUseCase.create(any())).willReturn(response);
        given(userUseCase.update(org.mockito.ArgumentMatchers.eq(USER_ID), any())).willReturn(response);

        mockMvc.perform(get("/api/admin/users/{id}", USER_ID).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userid").value("customer01"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mockMvc.perform(post("/api/admin/users")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"userid":"customer01","email":"customer@example.com",
                                 "password":"CustomerTest@2026!","name":"Customer","phone":"0901",
                                 "role":"ADMIN","restaurantId":"%s"}
                                """.formatted(RESTAURANT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.restaurantId").doesNotExist());
        mockMvc.perform(put("/api/admin/users/{id}", USER_ID)
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"updated@example.com\",\"name\":\"Updated\",\"phone\":\"0902\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void userAndAssignmentWritesRequireCsrf() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .with(user("admin").roles("ADMIN"))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/restaurant-assignments/{id}", USER_ID)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPasswordResetIsRoleAndCsrfProtected() throws Exception {
        String body = "{\"newPassword\":\"TemporaryPassword@2026!\"}";
        mockMvc.perform(patch("/api/admin/users/{id}/password", USER_ID)
                        .with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        for (String role : List.of("CUSTOMER", "OWNER", "STAFF")) {
            mockMvc.perform(patch("/api/admin/users/{id}/password", USER_ID)
                            .with(user(role.toLowerCase()).roles(role)).with(csrf())
                            .contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(patch("/api/admin/users/{id}/password", USER_ID)
                        .with(user("admin").roles("ADMIN"))
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminResetsPasswordWithoutReturningCredentials() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}/password", USER_ID)
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json")
                        .content("{\"newPassword\":\"TemporaryPassword@2026!\"}"))
                .andExpect(status().isNoContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));

        org.mockito.Mockito.verify(userUseCase).resetPassword(USER_ID, "TemporaryPassword@2026!");
    }

    @Test
    void adminListsAndMutatesAssignments() throws Exception {
        Assignment assignment = assignment();
        given(assignmentUseCase.findAll(0, 20, null, null, null))
                .willReturn(new AssignmentPage(List.of(assignment), 0, 20, 1, 1));
        given(assignmentUseCase.create(any())).willReturn(assignment);
        given(assignmentUseCase.update(org.mockito.ArgumentMatchers.eq(USER_ID), any())).willReturn(assignment);

        mockMvc.perform(get("/api/admin/restaurant-assignments").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].role").value("OWNER"))
                .andExpect(jsonPath("$.items[0].restaurantName").value("The Royal Pavilion"))
                .andExpect(jsonPath("$.items[0].assignedAt").doesNotExist());
        String body = "{\"userId\":\"%s\",\"restaurantId\":\"%s\",\"role\":\"OWNER\"}"
                .formatted(USER_ID, RESTAURANT_ID);
        mockMvc.perform(post("/api/admin/restaurant-assignments")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/admin/restaurant-assignments/{id}", USER_ID)
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/restaurant-assignments/{id}", USER_ID)
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
    }

    private AdminUser customer() {
        return new AdminUser(
                USER_ID, "customer01", "customer@example.com", "Customer", "0901", "CUSTOMER", "ACTIVE",
                null, null, NOW, NOW, NOW);
    }

    private Assignment assignment() {
        return new Assignment(
                USER_ID, "owner01", "Owner", "owner@example.com", "ACTIVE", "OWNER", RESTAURANT_ID,
                "royal-pavilion", "The Royal Pavilion", "DRAFT");
    }
}
