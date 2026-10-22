package com.sibang.hankki.location.application;

import com.sibang.hankki.location.application.exception.InvalidProvinceCodeException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LocationCatalogService implements LocationCatalogUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocationCatalogService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final Duration FALLBACK_CACHE_TTL = Duration.ofSeconds(60);

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
                provinces = cached(fetched, now, CACHE_TTL);
                return fetched;
            } catch (SourceUnavailableException exception) {
                if (cached != null) {
                    provinces = cached(cached.value(), now, FALLBACK_CACHE_TTL);
                    logFallback(exception, "stale-cache");
                    return cached.value();
                }
                List<Province> snapshot = List.copyOf(catalogPort.snapshotProvinces());
                if (!snapshot.isEmpty()) {
                    provinces = cached(snapshot, now, FALLBACK_CACHE_TTL);
                    logFallback(exception, "bundled-snapshot");
                    return snapshot;
                }
                throw unavailable(exception);
            }
        }
    }

    @Override
    public List<Ward> findWards(int provinceCode) {
        if (findProvinces().stream().noneMatch(province -> province.code() == provinceCode)) {
            throw new InvalidProvinceCodeException();
        }

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
                wardsByProvince.put(provinceCode, cached(fetched, now, CACHE_TTL));
                return fetched;
            } catch (SourceUnavailableException exception) {
                if (cached != null) {
                    wardsByProvince.put(provinceCode, cached(cached.value(), now, FALLBACK_CACHE_TTL));
                    logFallback(exception, "stale-cache");
                    return cached.value();
                }
                List<Ward> snapshot = List.copyOf(catalogPort.snapshotWards(provinceCode));
                if (!snapshot.isEmpty()) {
                    wardsByProvince.put(provinceCode, cached(snapshot, now, FALLBACK_CACHE_TTL));
                    logFallback(exception, "bundled-snapshot");
                    return snapshot;
                }
                throw unavailable(exception);
            }
        }
    }

    private void logFallback(SourceUnavailableException exception, String fallback) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        LOGGER.warn(
                "Location upstream unavailable: type={}, endpoint={}, exception={}, attempt={}, fallback={}",
                exception.dataType(), exception.endpoint(), cause.getClass().getSimpleName(),
                exception.attempt(), fallback);
    }

    private LocationCatalogUnavailableException unavailable(SourceUnavailableException exception) {
        logFallback(exception, "none");
        return new LocationCatalogUnavailableException();
    }

    private boolean isFresh(Cached<?> cached, Instant now) {
        return cached != null && now.isBefore(cached.expiresAt());
    }

    private <T> Cached<T> cached(T value, Instant now, Duration ttl) {
        return new Cached<>(value, now.plus(ttl));
    }

    private record Cached<T>(T value, Instant expiresAt) {
    }
}
