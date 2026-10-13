package com.sibang.hankki.admin.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sibang.hankki.admin.application.exception.InvalidAdminQueryException;
import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase;
import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase.AdminRestaurant;
import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase.AdminRestaurantPage;
import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase.AdminUser;
import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase.AdminUserPage;
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

@WebMvcTest(SystemAdminController.class)
@Import(SecurityConfig.class)
class SystemAdminControllerTest {

    private static final UUID USER_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-10-13T03:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemAdminReadUseCase readUseCase;

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerOwnerAndStaffAreForbidden() throws Exception {
        for (String role : List.of("CUSTOMER", "OWNER", "STAFF")) {
            mockMvc.perform(get("/api/admin/restaurants").with(user(role.toLowerCase()).roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminListsUsersWithDefaultsWithoutCredentialFields() throws Exception {
        given(readUseCase.findUsers(0, 20, null, null, null))
                .willReturn(new AdminUserPage(
                        List.of(new AdminUser(
                                USER_ID, "customer01", "customer@example.com", "Customer", "0900000000",
                                "CUSTOMER", "ACTIVE", null, NOW, NOW, NOW)),
                        0, 20, 1, 1));

        mockMvc.perform(get("/api/admin/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.items[0].userid").value("customer01"))
                .andExpect(jsonPath("$.items[0].role").value("CUSTOMER"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());

        verify(readUseCase).findUsers(0, 20, null, null, null);
    }

    @Test
    void adminListsRestaurantsWithFilters() throws Exception {
        given(readUseCase.findRestaurants(1, 10, "Royal", "ACTIVE", "ho-chi-minh"))
                .willReturn(new AdminRestaurantPage(
                        List.of(new AdminRestaurant(
                                RESTAURANT_ID, "royal-pavilion", "The Royal Pavilion", "ho-chi-minh",
                                "District 1", "District 1", "ACTIVE", NOW, NOW)),
                        1, 10, 1, 1));

        mockMvc.perform(get("/api/admin/restaurants")
                        .param("page", "1")
                        .param("size", "10")
                        .param("query", "Royal")
                        .param("status", "ACTIVE")
                        .param("citySlug", "ho-chi-minh")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].slug").value("royal-pavilion"))
                .andExpect(jsonPath("$.items[0].approvalStatus").value("ACTIVE"));
    }

    @Test
    void invalidParametersReturnBadRequest() throws Exception {
        given(readUseCase.findUsers(-1, 20, null, null, null))
                .willThrow(new InvalidAdminQueryException("invalid"));

        mockMvc.perform(get("/api/admin/users")
                        .param("page", "-1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/users")
                        .param("page", "not-a-number")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }
}
