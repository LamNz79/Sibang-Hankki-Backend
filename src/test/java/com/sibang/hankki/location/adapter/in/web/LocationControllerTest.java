package com.sibang.hankki.location.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sibang.hankki.auth.config.SecurityConfig;
import com.sibang.hankki.location.application.exception.LocationCatalogUnavailableException;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase.Province;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase.Ward;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LocationController.class)
@Import({SecurityConfig.class, LocationExceptionHandler.class})
class LocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocationCatalogUseCase locationCatalog;

    @Test
    void publicEndpointsReturnCanonicalLocationContracts() throws Exception {
        given(locationCatalog.findProvinces()).willReturn(List.of(
                new Province(79, "ho_chi_minh", "Thành phố Hồ Chí Minh", "thành phố trung ương")));
        given(locationCatalog.findWards(79)).willReturn(List.of(
                new Ward(26734, "phuong_ben_nghe", "Phường Bến Nghé", "phường", 79)));

        mockMvc.perform(get("/api/locations/provinces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value(79))
                .andExpect(jsonPath("$[0].codename").value("ho_chi_minh"));
        mockMvc.perform(get("/api/locations/provinces/79/wards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].provinceCode").value(79))
                .andExpect(jsonPath("$[0].name").value("Phường Bến Nghé"));
    }

    @Test
    void unavailableSourceWithoutCacheReturnsStandardServiceUnavailableError() throws Exception {
        given(locationCatalog.findProvinces()).willThrow(new LocationCatalogUnavailableException());

        mockMvc.perform(get("/api/locations/provinces"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.path").value("/api/locations/provinces"));
    }
}
