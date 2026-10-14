package com.sibang.hankki.location.application;

import com.sibang.hankki.location.application.exception.LocationCatalogUnavailableException;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase;
import com.sibang.hankki.location.application.port.out.LocationCatalogPort;
import com.sibang.hankki.location.application.port.out.LocationCatalogPort.SourceUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class LocationCatalogService implements LocationCatalogUseCase {

    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final LocationCatalogPort catalogPort;
    private final Clock clock;
    private Cached<List<Province>> provinces;
    private final Map<Integer, Cached<List<Ward>>> wardsByProvince = new HashMap<>();

    public LocationCatalogService(LocationCatalogPort catalogPort, Clock clock) {
        this.catalogPort = catalogPort;
        this.clock = clock;
    }

    @Override
    public synchronized List<Province> findProvinces() {
        Instant now = clock.instant();
        if (isFresh(provinces, now)) {
            return provinces.value();
        }
        try {
            List<Province> fetched = List.copyOf(catalogPort.fetchProvinces());
            provinces = new Cached<>(fetched, now);
            return fetched;
        } catch (SourceUnavailableException exception) {
            if (provinces != null) {
                return provinces.value();
            }
            throw new LocationCatalogUnavailableException();
        }
    }

    @Override
    public synchronized List<Ward> findWards(int provinceCode) {
        Instant now = clock.instant();
        Cached<List<Ward>> cached = wardsByProvince.get(provinceCode);
        if (isFresh(cached, now)) {
            return cached.value();
        }
        try {
            List<Ward> fetched = List.copyOf(catalogPort.fetchWards(provinceCode));
            wardsByProvince.put(provinceCode, new Cached<>(fetched, now));
            return fetched;
        } catch (SourceUnavailableException exception) {
            if (cached != null) {
                return cached.value();
            }
            throw new LocationCatalogUnavailableException();
        }
    }

    private boolean isFresh(Cached<?> cached, Instant now) {
        return cached != null && now.isBefore(cached.fetchedAt().plus(CACHE_TTL));
    }

    private record Cached<T>(T value, Instant fetchedAt) {
    }
}
