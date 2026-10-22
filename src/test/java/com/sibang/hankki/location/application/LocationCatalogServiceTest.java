package com.sibang.hankki.location.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.location.application.exception.InvalidProvinceCodeException;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase.Province;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase.Ward;
import com.sibang.hankki.location.application.port.out.LocationCatalogPort;
import com.sibang.hankki.location.application.port.out.LocationCatalogPort.SourceUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocationCatalogServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-14T02:00:00Z");
    private static final Province PROVINCE =
            new Province(79, "ho_chi_minh", "Thành phố Hồ Chí Minh", "thành phố trung ương");
    private static final Province OTHER_PROVINCE =
            new Province(1, "ha_noi", "Thành phố Hà Nội", "thành phố trung ương");
    private static final Ward WARD =
            new Ward(27169, "phuong_dien_hong", "Phường Diên Hồng", "phường", 79);

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
    void returnsStaleWardCacheWhenRefreshFails() {
        Instant expired = NOW.plusSeconds(24 * 60 * 60 + 1L);
        given(clock.instant()).willReturn(NOW, NOW, expired, expired);
        given(port.fetchProvinces()).willReturn(List.of(PROVINCE));
        given(port.fetchWards(79))
                .willReturn(List.of(WARD))
                .willThrow(new SourceUnavailableException("down", new RuntimeException()));
        var service = new LocationCatalogService(port, clock);

        assertThat(service.findWards(79)).containsExactly(WARD);
        assertThat(service.findWards(79)).containsExactly(WARD);

        verify(port, times(2)).fetchProvinces();
        verify(port, times(2)).fetchWards(79);
    }

    @Test
    void usesBundledSnapshotWhenSourceIsDownAndNoCacheExists() {
        given(clock.instant()).willReturn(NOW, NOW);
        given(port.fetchProvinces())
                .willThrow(new SourceUnavailableException("down", new RuntimeException()));
        given(port.snapshotProvinces()).willReturn(List.of(PROVINCE));
        given(port.fetchWards(79))
                .willThrow(new SourceUnavailableException("down", new RuntimeException()));
        given(port.snapshotWards(79)).willReturn(List.of(WARD));
        var service = new LocationCatalogService(port, clock);

        assertThat(service.findProvinces()).containsExactly(PROVINCE);
        assertThat(service.findWards(79)).containsExactly(WARD);

        verify(port, times(2)).fetchProvinces();
        verify(port).fetchWards(79);
    }

    @Test
    void rejectsUnknownProvinceWithoutFetchingOrCachingWards() {
        given(clock.instant()).willReturn(NOW);
        given(port.fetchProvinces()).willReturn(List.of(PROVINCE));
        var service = new LocationCatalogService(port, clock);

        assertThatThrownBy(() -> service.findWards(1))
                .isInstanceOf(InvalidProvinceCodeException.class);

        verify(port, never()).fetchWards(anyInt());
    }

    @Test
    void slowWardRefreshDoesNotBlockOtherCatalogRequests() throws Exception {
        CountDownLatch slowWardStarted = new CountDownLatch(1);
        CountDownLatch releaseSlowWard = new CountDownLatch(1);
        LocationCatalogPort blockingPort = new LocationCatalogPort() {
            @Override
            public List<Province> fetchProvinces() {
                return List.of(PROVINCE, OTHER_PROVINCE);
            }

            @Override
            public List<Ward> fetchWards(int provinceCode) {
                if (provinceCode == 79) {
                    slowWardStarted.countDown();
                    try {
                        releaseSlowWard.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new SourceUnavailableException("interrupted", exception);
                    }
                    return List.of(WARD);
                }
                return List.of(new Ward(1, "phuong_khac", "Phường khác", "phường", provinceCode));
            }

            @Override
            public List<Province> snapshotProvinces() {
                return List.of(PROVINCE, OTHER_PROVINCE);
            }

            @Override
            public List<Ward> snapshotWards(int provinceCode) {
                return List.of();
            }
        };
        var service = new LocationCatalogService(blockingPort, Clock.fixed(NOW, Clock.systemUTC().getZone()));
        var executor = Executors.newFixedThreadPool(3);

        try {
            var slowRequest = executor.submit(() -> service.findWards(79));
            assertThat(slowWardStarted.await(1, TimeUnit.SECONDS)).isTrue();

            assertThat(executor.submit(service::findProvinces).get(1, TimeUnit.SECONDS))
                    .containsExactly(PROVINCE, OTHER_PROVINCE);
            assertThat(executor.submit(() -> service.findWards(1)).get(1, TimeUnit.SECONDS))
                    .extracting(Ward::provinceCode)
                    .containsExactly(1);

            releaseSlowWard.countDown();
            assertThat(slowRequest.get(1, TimeUnit.SECONDS)).containsExactly(WARD);
        } finally {
            releaseSlowWard.countDown();
            executor.shutdownNow();
        }
    }
}
