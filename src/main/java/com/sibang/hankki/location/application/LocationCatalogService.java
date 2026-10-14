package com.sibang.hankki.location.application;

import com.sibang.hankki.location.application.exception.LocationCatalogUnavailableException;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase;
import com.sibang.hankki.location.application.port.out.LocationCatalogPort;
import com.sibang.hankki.location.application.port.out.LocationCatalogPort.SourceUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class LocationCatalogService implements LocationCatalogUseCase {

    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final LocationCatalogPort catalogPort;
    private final Clock clock;
    private final Object provinceRefreshLock = new Object();
    private volatile Cached<List<Province>> provinces;
    private final ConcurrentMap<Integer, Cached<List<Ward>>> wardsByProvince = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, Object> wardRefreshLocks = new ConcurrentHashMap<>();

    public LocationCatalogService(LocationCatalogPort catalogPort, Clock clock) {
        this.catalogPort = catalogPort;
        this.clock = clock;
    }

    @Override
    public List<Province> findProvinces() {
        Instant now = clock.instant();
        Cached<List<Province>> cached = provinces;
        if (isFresh(cached, now)) {
            return cached.value();
        }

        synchronized (provinceRefreshLock) {
            cached = provinces;
            if (isFresh(cached, now)) {
                return cached.value();
            }
            try {
                List<Province> fetched = List.copyOf(catalogPort.fetchProvinces());
                provinces = new Cached<>(fetched, now);
                return fetched;
            } catch (SourceUnavailableException exception) {
                if (cached != null) {
                    return cached.value();
                }
                throw new LocationCatalogUnavailableException();
            }
        }
    }

    @Override
    public List<Ward> findWards(int provinceCode) {
        Instant now = clock.instant();
        Cached<List<Ward>> cached = wardsByProvince.get(provinceCode);
        if (isFresh(cached, now)) {
            return cached.value();
        }

        Object refreshLock = wardRefreshLocks.computeIfAbsent(provinceCode, ignored -> new Object());
        synchronized (refreshLock) {
            cached = wardsByProvince.get(provinceCode);
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
    }

    private boolean isFresh(Cached<?> cached, Instant now) {
        return cached != null && now.isBefore(cached.fetchedAt().plus(CACHE_TTL));
    }

    private record Cached<T>(T value, Instant fetchedAt) {
    }
}
