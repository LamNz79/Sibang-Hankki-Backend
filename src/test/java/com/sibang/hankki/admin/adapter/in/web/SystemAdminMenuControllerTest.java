package com.sibang.hankki.admin.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.port.in.SystemAdminMenuUseCase;
import com.sibang.hankki.auth.OwnerSessionUserDetailsService;
import com.sibang.hankki.auth.SessionUser;
import com.sibang.hankki.auth.config.SecurityConfig;
import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.exception.MenuResourceNotFoundException;
import com.sibang.hankki.restaurant.application.model.OwnerMenu;
import com.sibang.hankki.restaurant.application.model.OwnerMenuItemUpdate;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(SystemAdminMenuController.class)
@Import(SecurityConfig.class)
class SystemAdminMenuControllerTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ADMIN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CATEGORY_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ITEM_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemAdminMenuUseCase useCase;

    @MockitoBean
    private OwnerSessionUserDetailsService userDetailsService;

    @BeforeEach
    void keepMockAdminSessionActive() {
        given(userDetailsService.isActive(any(SessionUser.class))).willReturn(true);
    }

    @Test
    void onlyAdminCanReadMenu() throws Exception {
        mockMvc.perform(get(path(""))).andExpect(status().isUnauthorized());

        for (String role : List.of("CUSTOMER", "OWNER", "STAFF")) {
            mockMvc.perform(get(path("")).with(user(role.toLowerCase()).roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminPerformsCategoryAndItemCrudWithItsActorId() throws Exception {
        OwnerMenu.Category category = new OwnerMenu.Category(CATEGORY_ID, "Main dishes", 1);
        OwnerMenu.Item item = new OwnerMenu.Item(
                ITEM_ID, CATEGORY_ID, "Grilled beef", "With vegetables",
                new BigDecimal("250000.00"), "VND", "https://example.com/image.jpg", true);
        given(useCase.get(RESTAURANT_ID)).willReturn(new OwnerMenu(List.of(category), List.of(item)));
        given(useCase.createCategory(RESTAURANT_ID, ADMIN_ID, "Main dishes", 1)).willReturn(category);
        given(useCase.updateCategory(RESTAURANT_ID, ADMIN_ID, CATEGORY_ID, "Main courses", 2))
                .willReturn(new OwnerMenu.Category(CATEGORY_ID, "Main courses", 2));
        given(useCase.createItem(eq(RESTAURANT_ID), eq(ADMIN_ID), any())).willReturn(item);
        given(useCase.updateItem(eq(RESTAURANT_ID), eq(ADMIN_ID), eq(ITEM_ID), any())).willReturn(item);

        mockMvc.perform(get(path("")).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].id").value(CATEGORY_ID.toString()))
                .andExpect(jsonPath("$.items[0].available").value(true));
        mockMvc.perform(post(path("/categories")).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Main dishes\",\"displayOrder\":1}"))
                .andExpect(status().isCreated());
        mockMvc.perform(put(path("/categories/" + CATEGORY_ID)).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Main courses\",\"displayOrder\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Main courses"));
        mockMvc.perform(post(path("/items")).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(itemRequest()))
                .andExpect(status().isCreated());
        mockMvc.perform(put(path("/items/" + ITEM_ID)).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(itemRequest()))
                .andExpect(status().isOk());
        mockMvc.perform(delete(path("/items/" + ITEM_ID)).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(path("/categories/" + CATEGORY_ID)).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(useCase).deleteItem(RESTAURANT_ID, ADMIN_ID, ITEM_ID);
        verify(useCase).deleteCategory(RESTAURANT_ID, ADMIN_ID, CATEGORY_ID);
        verify(useCase).createItem(eq(RESTAURANT_ID), eq(ADMIN_ID), any(OwnerMenuItemUpdate.class));
    }

    @Test
    void mutationsRequireCsrf() throws Exception {
        mockMvc.perform(post(path("/categories")).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Main dishes\",\"displayOrder\":1}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(path("/items/" + ITEM_ID)).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(itemRequest()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(path("/categories/" + CATEGORY_ID)).with(admin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidPayloadAndMissingResourcesUseAdminErrors() throws Exception {
        given(useCase.createCategory(RESTAURANT_ID, ADMIN_ID, "", -1))
                .willThrow(new InvalidBookingRequestException("invalid category"));
        given(useCase.get(RESTAURANT_ID))
                .willThrow(new AdminResourceNotFoundException("Restaurant not found"));
        given(useCase.updateItem(eq(RESTAURANT_ID), eq(ADMIN_ID), eq(ITEM_ID), any()))
                .willThrow(new MenuResourceNotFoundException());

        mockMvc.perform(post(path("/categories")).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"displayOrder\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(get(path("")).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(put(path("/items/" + ITEM_ID)).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(itemRequest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private RequestPostProcessor admin() {
        SessionUser principal = mock(SessionUser.class);
        given(principal.id()).willReturn(ADMIN_ID);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private String path(String suffix) {
        return "/api/admin/restaurants/" + RESTAURANT_ID + "/menu" + suffix;
    }

    private String itemRequest() {
        return """
                {
                  "categoryId": "%s",
                  "name": "Grilled beef",
                  "description": "With vegetables",
                  "price": 250000,
                  "currency": "VND",
                  "imageUrl": "https://example.com/image.jpg",
                  "available": true
                }
                """.formatted(CATEGORY_ID);
    }
}
