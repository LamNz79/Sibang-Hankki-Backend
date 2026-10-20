package com.sibang.hankki.favorite.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
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
import com.sibang.hankki.favorite.application.port.in.CustomerFavoriteUseCase;
import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;
import com.sibang.hankki.restaurant.application.model.RestaurantSummaryResponse;
import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import com.sibang.hankki.user.adapter.out.persistence.repository.UserAuthenticationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(CustomerFavoriteController.class)
@Import({SecurityConfig.class, OwnerSessionUserDetailsService.class})
class CustomerFavoriteControllerTest {

    private static final String PASSWORD = "test-password";
    private static final UUID CUSTOMER_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private CustomerFavoriteUseCase useCase;
    @MockitoBean
    private UserAuthenticationRepository repository;

    @BeforeEach
    void configureUsers() {
        configureUser("customer", "CUSTOMER", CUSTOMER_ID);
        configureUser("owner", "OWNER", UUID.randomUUID());
        configureUser("staff", "STAFF", UUID.randomUUID());
        configureUser("admin", "ADMIN", UUID.randomUUID());
    }

    @Test
    void anonymousIsUnauthorizedAndOtherRolesAreForbidden() throws Exception {
        mockMvc.perform(get("/api/customer/favorites")).andExpect(status().isUnauthorized());

        for (String userid : List.of("owner", "staff", "admin")) {
            mockMvc.perform(get("/api/customer/favorites").session(login(userid)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(useCase);
    }

    @Test
    void customerListsFavoritesUsingAuthenticatedId() throws Exception {
        given(useCase.favorites(CUSTOMER_ID)).willReturn(List.of(summary()));

        mockMvc.perform(get("/api/customer/favorites").session(login("customer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("anan-saigon"))
                .andExpect(jsonPath("$[0].slotMatrix").doesNotExist());

        verify(useCase).favorites(CUSTOMER_ID);
    }

    @Test
    void saveAndDeleteRequireCsrfAndUseAuthenticatedId() throws Exception {
        MockHttpSession session = login("customer");
        String path = "/api/customer/favorites/anan-saigon";

        mockMvc.perform(put(path).session(session)).andExpect(status().isForbidden());
        mockMvc.perform(delete(path).session(session)).andExpect(status().isForbidden());
        mockMvc.perform(put(path).with(csrf()).session(session)).andExpect(status().isNoContent());
        mockMvc.perform(delete(path).with(csrf()).session(session)).andExpect(status().isNoContent());

        verify(useCase).save(CUSTOMER_ID, "anan-saigon");
        verify(useCase).delete(CUSTOMER_ID, "anan-saigon");
    }

    @Test
    void unavailableRestaurantReturnsNotFound() throws Exception {
        willThrow(new RestaurantNotFoundException()).given(useCase).save(CUSTOMER_ID, "unavailable");

        mockMvc.perform(put("/api/customer/favorites/unavailable")
                        .with(csrf()).session(login("customer")))
                .andExpect(status().isNotFound());
    }

    private void configureUser(String userid, String role, UUID id) {
        UserEntity user = org.mockito.Mockito.mock(UserEntity.class);
        given(user.getId()).willReturn(id);
        given(user.getUserid()).willReturn(userid);
        given(user.getPasswordHash()).willReturn(passwordEncoder.encode(PASSWORD));
        given(user.getRole()).willReturn(role);
        given(repository.findByUseridAndStatusAndDeletedAtIsNull(userid, "ACTIVE"))
                .willReturn(Optional.of(user));
    }

    private MockHttpSession login(String userid) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("userid", userid)
                        .param("password", PASSWORD))
                .andExpect(status().isNoContent())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private RestaurantSummaryResponse summary() {
        return new RestaurantSummaryResponse(
                "anan-saigon", "Anan Saigon", "ho-chi-minh-city", "District 1",
                "Vietnamese contemporary", "vietnamese", 4.7, "#f6ede4",
                "https://example.test/anan.jpg", "Anan Saigon", "Available", "18:30",
                "under300", List.of("michelin"));
    }
}
