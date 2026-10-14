package com.sibang.hankki.admin.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.exception.InvalidAdminQueryException;
import com.sibang.hankki.admin.application.exception.InvalidAdminStatusTransitionException;
import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase;
import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase.AdminRestaurant;
import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase.AdminRestaurantPage;
import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase.AdminUser;
import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase.AdminUserPage;
import com.sibang.hankki.admin.application.port.in.SystemAdminRestaurantUseCase;
import com.sibang.hankki.admin.application.port.in.SystemAdminRestaurantUseCase.AdminRestaurantDetail;
import com.sibang.hankki.admin.application.port.in.SystemAdminRestaurantUseCase.CreateRestaurantCommand;
import com.sibang.hankki.admin.application.port.in.SystemAdminRestaurantUseCase.UpdateRestaurantCommand;
import com.sibang.hankki.admin.application.port.in.SystemAdminStatusUseCase;
import com.sibang.hankki.auth.config.SecurityConfig;
import com.sibang.hankki.location.application.exception.LocationCatalogUnavailableException;
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

    @MockitoBean
    private SystemAdminStatusUseCase statusUseCase;

    @MockitoBean
    private SystemAdminRestaurantUseCase restaurantUseCase;

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
        given(readUseCase.findRestaurants(1, 10, "Royal", "ACTIVE", "ho-chi-minh", 79))
                .willReturn(new AdminRestaurantPage(
                        List.of(new AdminRestaurant(
                                RESTAURANT_ID, "royal-pavilion", "The Royal Pavilion", "ho-chi-minh",
                                "District 1", "Phường Bến Nghé", 79, "Thành phố Hồ Chí Minh", "ACTIVE", NOW, NOW)),
                        1, 10, 1, 1));

        mockMvc.perform(get("/api/admin/restaurants")
                        .param("page", "1")
                        .param("size", "10")
                        .param("query", "Royal")
                        .param("status", "ACTIVE")
                        .param("citySlug", "ho-chi-minh")
                        .param("provinceCode", "79")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].slug").value("royal-pavilion"))
                .andExpect(jsonPath("$.items[0].provinceCode").value(79))
                .andExpect(jsonPath("$.items[0].provinceName").value("Thành phố Hồ Chí Minh"))
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

    @Test
    void onlyAdminCanUpdateStatuses() throws Exception {
        for (String path : List.of(
                "/api/admin/users/" + USER_ID + "/status",
                "/api/admin/restaurants/" + RESTAURANT_ID + "/status")) {
            mockMvc.perform(patch(path)
                            .with(csrf())
                            .contentType("application/json")
                            .content("{\"status\":\"ACTIVE\"}"))
                    .andExpect(status().isUnauthorized());

            for (String role : List.of("CUSTOMER", "OWNER", "STAFF")) {
                mockMvc.perform(patch(path)
                                .with(user(role.toLowerCase()).roles(role))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"status\":\"ACTIVE\"}"))
                        .andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void adminUpdatesUserAndRestaurantStatusWithCsrf() throws Exception {
        given(statusUseCase.updateUserStatus(USER_ID, "SUSPENDED"))
                .willReturn(new AdminUser(
                        USER_ID, "customer01", "customer@example.com", "Customer", null,
                        "CUSTOMER", "SUSPENDED", null, NOW, NOW, NOW));
        given(statusUseCase.updateRestaurantStatus(RESTAURANT_ID, "SUSPENDED"))
                .willReturn(new AdminRestaurant(
                        RESTAURANT_ID, "royal-pavilion", "The Royal Pavilion", "ho-chi-minh",
                        "District 1", "District 1", "SUSPENDED", NOW, NOW));

        mockMvc.perform(patch("/api/admin/users/{id}/status", USER_ID)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(patch("/api/admin/restaurants/{id}/status", RESTAURANT_ID)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RESTAURANT_ID.toString()))
                .andExpect(jsonPath("$.approvalStatus").value("SUSPENDED"));
    }

    @Test
    void adminStatusUpdatesRequireCsrf() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}/status", USER_ID)
                        .with(user("admin").roles("ADMIN"))
                        .contentType("application/json")
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusErrorsUseAdminApiErrorResponses() throws Exception {
        given(statusUseCase.updateUserStatus(USER_ID, "CLOSED"))
                .willThrow(new InvalidAdminQueryException("invalid"));
        given(statusUseCase.updateUserStatus(USER_ID, "SUSPENDED"))
                .willThrow(new InvalidAdminStatusTransitionException("invalid transition"));
        given(statusUseCase.updateRestaurantStatus(RESTAURANT_ID, "ACTIVE"))
                .willThrow(new AdminResourceNotFoundException("not found"));

        mockMvc.perform(patch("/api/admin/users/{id}/status", USER_ID)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(patch("/api/admin/users/{id}/status", USER_ID)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(patch("/api/admin/restaurants/{id}/status", RESTAURANT_ID)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void restaurantCrudIsAdminOnly() throws Exception {
        for (String role : List.of("CUSTOMER", "OWNER", "STAFF")) {
            mockMvc.perform(get("/api/admin/restaurants/{id}", RESTAURANT_ID)
                            .with(user(role.toLowerCase()).roles(role)))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(get("/api/admin/restaurants/{id}", RESTAURANT_ID))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/restaurants")
                        .with(user("customer").roles("CUSTOMER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(createRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadCreateUpdateAndArchiveRestaurant() throws Exception {
        given(restaurantUseCase.findById(RESTAURANT_ID)).willReturn(detail("Restaurant", "DRAFT"));
        given(restaurantUseCase.create(any(CreateRestaurantCommand.class))).willReturn(detail("Restaurant", "DRAFT"));
        given(restaurantUseCase.update(eq(RESTAURANT_ID), any(UpdateRestaurantCommand.class)))
                .willReturn(detail("Updated Restaurant", "DRAFT"));

        mockMvc.perform(get("/api/admin/restaurants/{id}", RESTAURANT_ID)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("restaurant-slug"))
                .andExpect(jsonPath("$.description").value("Description"))
                .andExpect(jsonPath("$.provinceCode").value(79))
                .andExpect(jsonPath("$.wardCode").value(26734))
                .andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"));

        mockMvc.perform(post("/api/admin/restaurants")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(createRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.approvalStatus").value("DRAFT"));

        mockMvc.perform(put("/api/admin/restaurants/{id}", RESTAURANT_ID)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(updateRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Restaurant"));

        mockMvc.perform(delete("/api/admin/restaurants/{id}", RESTAURANT_ID)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(restaurantUseCase).archive(RESTAURANT_ID);
    }

    @Test
    void restaurantWritesRequireCsrf() throws Exception {
        mockMvc.perform(post("/api/admin/restaurants")
                        .with(user("admin").roles("ADMIN"))
                        .contentType("application/json")
                        .content(createRequest()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/restaurants/{id}", RESTAURANT_ID)
                        .with(user("admin").roles("ADMIN"))
                        .contentType("application/json")
                        .content(updateRequest()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/restaurants/{id}", RESTAURANT_ID)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void restaurantCreateReturnsServiceUnavailableWhenLocationCatalogHasNoCache() throws Exception {
        given(restaurantUseCase.create(any(CreateRestaurantCommand.class)))
                .willThrow(new LocationCatalogUnavailableException());

        mockMvc.perform(post("/api/admin/restaurants")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(createRequest()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    private AdminRestaurantDetail detail(String name, String status) {
        return new AdminRestaurantDetail(
                RESTAURANT_ID, "restaurant-slug", name, "Description", "Vietnamese", "Vietnamese",
                "ho-chi-minh", "District 1", "Phường Bến Nghé", 79, "Thành phố Hồ Chí Minh", 26734,
                "Phường Bến Nghé", "1 Main Street", "0900000000",
                "restaurant@example.com", "Asia/Ho_Chi_Minh", "$$", status, NOW, NOW);
    }

    private String createRequest() {
        return """
                {
                  "slug": "restaurant-slug",
                  "name": "Restaurant",
                  "timezone": "Asia/Ho_Chi_Minh",
                  "provinceCode": 79,
                  "wardCode": 26734
                }
                """;
    }

    private String updateRequest() {
        return """
                {
                  "name": "Updated Restaurant",
                  "timezone": "Asia/Ho_Chi_Minh",
                  "provinceCode": 79,
                  "wardCode": 26734
                }
                """;
    }
}
