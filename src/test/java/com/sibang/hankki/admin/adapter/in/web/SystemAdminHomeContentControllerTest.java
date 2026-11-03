package com.sibang.hankki.admin.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sibang.hankki.auth.OwnerSessionUserDetailsService;
import com.sibang.hankki.auth.SessionUser;
import com.sibang.hankki.auth.config.SecurityConfig;
import com.sibang.hankki.home.application.exception.InvalidHomeContentException;
import com.sibang.hankki.home.application.model.HomeContent;
import com.sibang.hankki.home.application.port.in.HomeContentUseCase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SystemAdminHomeContentController.class)
@Import(SecurityConfig.class)
class SystemAdminHomeContentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HomeContentUseCase useCase;

    @MockitoBean
    private OwnerSessionUserDetailsService userDetailsService;

    @BeforeEach
    void keepMockSessionsActive() {
        given(userDetailsService.isActive(any(SessionUser.class))).willReturn(true);
    }

    @Test
    void onlyAdminCanReadConfiguration() throws Exception {
        mockMvc.perform(get("/api/admin/home-content"))
                .andExpect(status().isUnauthorized());

        for (String role : List.of("CUSTOMER", "OWNER", "STAFF")) {
            mockMvc.perform(get("/api/admin/home-content")
                            .with(user(role.toLowerCase()).roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminReadsAndUpdatesContent() throws Exception {
        HomeContent.Hero hero = hero();
        HomeContent.City city = city();
        given(useCase.getAdmin()).willReturn(new HomeContent(hero, List.of(city)));
        given(useCase.updateHero(any())).willReturn(hero);
        given(useCase.upsertCity(org.mockito.ArgumentMatchers.eq(79), any())).willReturn(city);

        mockMvc.perform(get("/api/admin/home-content").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heroBanner.titleEn").value("Title"))
                .andExpect(jsonPath("$.cityImages[0].citySlug").value("ho-chi-minh"));
        mockMvc.perform(put("/api/admin/home-content/hero")
                        .with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(heroRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("/images/hero.jpg"));
        mockMvc.perform(put("/api/admin/home-content/cities/79")
                        .with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(cityRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provinceCode").value(79));
    }

    @Test
    void mutationsRequireAdminAndCsrf() throws Exception {
        mockMvc.perform(put("/api/admin/home-content/hero")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(heroRequest()))
                .andExpect(status().isForbidden());

        for (String role : List.of("CUSTOMER", "OWNER", "STAFF")) {
            mockMvc.perform(put("/api/admin/home-content/hero")
                            .with(user(role.toLowerCase()).roles(role)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(heroRequest()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void invalidContentUsesAdminErrorResponse() throws Exception {
        given(useCase.updateHero(any()))
                .willThrow(new InvalidHomeContentException("imageUrl is invalid"));

        mockMvc.perform(put("/api/admin/home-content/hero")
                        .with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(heroRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }

    private HomeContent.Hero hero() {
        return new HomeContent.Hero(
                "Tiêu đề", "Title", "제목", "Mô tả", "Description", "설명",
                "/images/hero.jpg", "Ảnh", "Image", "이미지", "/restaurants/example", true);
    }

    private HomeContent.City city() {
        return new HomeContent.City(
                79, "ho-chi-minh", "/images/city.jpg", "Ho Chi Minh City", 1, true);
    }

    private String heroRequest() {
        return """
                {
                  "titleVi": "Tiêu đề",
                  "titleEn": "Title",
                  "titleKo": "제목",
                  "imageUrl": "/images/hero.jpg",
                  "targetUrl": "/restaurants/example",
                  "enabled": true
                }
                """;
    }

    private String cityRequest() {
        return """
                {
                  "citySlug": "ho-chi-minh",
                  "imageUrl": "/images/city.jpg",
                  "imageAlt": "Ho Chi Minh City",
                  "sortOrder": 1,
                  "enabled": true
                }
                """;
    }
}
