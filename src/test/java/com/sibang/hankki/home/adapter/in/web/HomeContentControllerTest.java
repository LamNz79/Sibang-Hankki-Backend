package com.sibang.hankki.home.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sibang.hankki.auth.OwnerSessionUserDetailsService;
import com.sibang.hankki.auth.config.SecurityConfig;
import com.sibang.hankki.home.application.model.PublicHomeContent;
import com.sibang.hankki.home.application.port.in.HomeContentUseCase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HomeContentController.class)
@Import(SecurityConfig.class)
class HomeContentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HomeContentUseCase useCase;

    @MockitoBean
    private OwnerSessionUserDetailsService userDetailsService;

    @Test
    void anonymousCanReadHomeContent() throws Exception {
        given(useCase.getPublic("vi")).willReturn(new PublicHomeContent(
                new PublicHomeContent.HeroBanner(
                        "Khám phá", "Mô tả", "/images/hero.jpg", "Không gian", "/restaurants/example"),
                List.of(new PublicHomeContent.CityImage(
                        79, "ho-chi-minh", "/images/city.jpg", "Ho Chi Minh City", 1))));

        mockMvc.perform(get("/api/home-content").param("locale", "vi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heroBanner.title").value("Khám phá"))
                .andExpect(jsonPath("$.cityImages[0].provinceCode").value(79));
    }
}
