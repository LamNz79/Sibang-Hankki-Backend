package com.sibang.hankki.location.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.location.application.exception.LocationCatalogUnavailableException;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase.Province;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase.Ward;
import com.sibang.hankki.location.application.port.out.LocationCatalogPort;
import com.sibang.hankki.location.application.port.out.LocationCatalogPort.SourceUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocationCatalogServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-14T02:00:00Z");
    private static final Province PROVINCE =
            new Province(79, "ho_chi_minh", "Thành phố Hồ Chí Minh", "thành phố trung ương");
    private static final Ward WARD =
            new Ward(26734, "phuong_ben_nghe", "Phường Bến Nghé", "phường", 79);

    @Mock
    private LocationCatalogPort port;

    @Mock
    private Clock clock;

    @Test
    void cachesProvinceAndWardResultsForTwentyFourHours() {
        given(clock.instant()).willReturn(NOW, NOW.plusSeconds(60), NOW, NOW.plusSeconds(60));
        given(port.fetchProvinces()).willReturn(List.of(PROVINCE));
        given(port.fetchWards(79)).willReturn(List.of(WARD));
        var service = new LocationCatalogService(port, clock);

        assertThat(service.findProvinces()).containsExactly(PROVINCE);
        assertThat(service.findProvinces()).containsExactly(PROVINCE);
        assertThat(service.findWards(79)).containsExactly(WARD);
        assertThat(service.findWards(79)).containsExactly(WARD);

        verify(port).fetchProvinces();
        verify(port).fetchWards(79);
    }

    @Test
    void returnsStaleCacheWhenRefreshFails() {
        given(clock.instant()).willReturn(NOW, NOW.plusSeconds(24 * 60 * 60 + 1L));
        given(port.fetchProvinces())
                .willReturn(List.of(PROVINCE))
                .willThrow(new SourceUnavailableException("down", new RuntimeException()));
        var service = new LocationCatalogService(port, clock);

        assertThat(service.findProvinces()).containsExactly(PROVINCE);
        assertThat(service.findProvinces()).containsExactly(PROVINCE);

        verify(port, times(2)).fetchProvinces();
    }

    @Test
    void failsClearlyWhenSourceIsDownAndNoCacheExists() {
        given(clock.instant()).willReturn(NOW);
        given(port.fetchWards(79))
                .willThrow(new SourceUnavailableException("down", new RuntimeException()));
        var service = new LocationCatalogService(port, clock);

        assertThatThrownBy(() -> service.findWards(79))
                .isInstanceOf(LocationCatalogUnavailableException.class);
    }
}
