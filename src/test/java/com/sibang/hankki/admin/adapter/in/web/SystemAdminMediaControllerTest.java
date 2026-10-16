package com.sibang.hankki.admin.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.port.in.SystemAdminMediaUseCase;
import com.sibang.hankki.auth.OwnerSessionUserDetailsService;
import com.sibang.hankki.auth.SessionUser;
import com.sibang.hankki.auth.config.SecurityConfig;
import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.exception.OwnerMediaNotFoundException;
import com.sibang.hankki.restaurant.application.model.OwnerMedia;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SystemAdminMediaController.class)
@Import(SecurityConfig.class)
class SystemAdminMediaControllerTest {

    private static final UUID RESTAURANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID MEDIA_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemAdminMediaUseCase useCase;

    @MockitoBean
    private OwnerSessionUserDetailsService userDetailsService;

    @BeforeEach
    void keepMockSessionsActive() {
        given(userDetailsService.isActive(any(SessionUser.class))).willReturn(true);
    }

    @Test
    void onlyAdminCanReadMedia() throws Exception {
        mockMvc.perform(get(path(""))).andExpect(status().isUnauthorized());

        for (String role : List.of("CUSTOMER", "OWNER", "STAFF")) {
            mockMvc.perform(get(path("")).with(user(role.toLowerCase()).roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminPerformsCompleteMediaCrud() throws Exception {
        OwnerMedia.Image image = new OwnerMedia.Image(
                MEDIA_ID, "https://example.com/restaurant.jpg", "Dining room", 0);
        given(useCase.get(RESTAURANT_ID)).willReturn(new OwnerMedia(List.of(image)));
        given(useCase.create(RESTAURANT_ID, update())).willReturn(image);
        given(useCase.update(RESTAURANT_ID, MEDIA_ID, update())).willReturn(image);

        mockMvc.perform(get(path("")).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images[0].id").value(MEDIA_ID.toString()))
                .andExpect(jsonPath("$.images[0].imageUrl")
                        .value("https://example.com/restaurant.jpg"));
        mockMvc.perform(post(path("")).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request()))
                .andExpect(status().isCreated());
        mockMvc.perform(put(path("/" + MEDIA_ID)).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request()))
                .andExpect(status().isOk());
        mockMvc.perform(delete(path("/" + MEDIA_ID)).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(useCase).delete(RESTAURANT_ID, MEDIA_ID);
    }

    @Test
    void mutationsRequireCsrf() throws Exception {
        mockMvc.perform(post(path("")).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(request()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(path("/" + MEDIA_ID)).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(request()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(path("/" + MEDIA_ID)).with(admin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidPayloadAndMissingResourcesUseAdminErrors() throws Exception {
        OwnerMedia.Update invalid = new OwnerMedia.Update("file:///secret.jpg", null, 0);
        given(useCase.create(RESTAURANT_ID, invalid))
                .willThrow(new InvalidBookingRequestException("invalid image"));
        given(useCase.get(RESTAURANT_ID))
                .willThrow(new AdminResourceNotFoundException("Restaurant not found"));
        given(useCase.update(RESTAURANT_ID, MEDIA_ID, update()))
                .willThrow(new OwnerMediaNotFoundException());

        mockMvc.perform(post(path("")).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageUrl\":\"file:///secret.jpg\",\"sortOrder\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(get(path("")).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(put(path("/" + MEDIA_ID)).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }

    private String path(String suffix) {
        return "/api/admin/restaurants/" + RESTAURANT_ID + "/media" + suffix;
    }

    private OwnerMedia.Update update() {
        return new OwnerMedia.Update(
                "https://example.com/restaurant.jpg", "Dining room", 0);
    }

    private String request() {
        return """
                {
                  "imageUrl": "https://example.com/restaurant.jpg",
                  "altText": "Dining room",
                  "sortOrder": 0
                }
                """;
    }
}
