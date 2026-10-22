package com.sibang.hankki.location.adapter.out.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase.Province;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase.Ward;
import com.sibang.hankki.location.application.port.out.LocationCatalogPort;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResourceAccessException;

@Component
public class VietnamProvincesHttpAdapter implements LocationCatalogPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(VietnamProvincesHttpAdapter.class);
    private static final String SNAPSHOT_PATH = "location/vietnam-administrative-snapshot.json";

    private final RestClient restClient;
    private final int maxRetries;
    private final Duration retryDelay;
    private final List<ProvincePayload> snapshot;

    public VietnamProvincesHttpAdapter(
            @Qualifier("locationRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            @Value("${app.location-api.max-retries:${APP_LOCATION_API_MAX_RETRIES:2}}") int maxRetries,
            @Value("${app.location-api.retry-delay:${APP_LOCATION_API_RETRY_DELAY:200ms}}") Duration retryDelay) {
        if (maxRetries < 0 || maxRetries > 2) {
            throw new IllegalArgumentException("Location API max retries must be between 0 and 2");
        }
        if (retryDelay.isNegative()) {
            throw new IllegalArgumentException("Location API retry delay must not be negative");
        }
        this.restClient = restClient;
        this.maxRetries = maxRetries;
        this.retryDelay = retryDelay;
        this.snapshot = readSnapshot(objectMapper);
    }

    @Override
    public List<Province> fetchProvinces() {
        ProvincePayload[] response = fetch(
                "provinces", "/p/",
                () -> restClient.get().uri("/p/").retrieve().body(ProvincePayload[].class));
        return response == null ? List.of() : Arrays.stream(response).map(this::province).toList();
    }

    @Override
    public List<Ward> fetchWards(int provinceCode) {
        String endpoint = "/w/?province=" + provinceCode;
        WardPayload[] response = fetch(
                "wards", endpoint,
                () -> restClient.get()
                        .uri(builder -> builder.path("/w/").queryParam("province", provinceCode).build())
                        .retrieve()
                        .body(WardPayload[].class));
        return response == null ? List.of() : Arrays.stream(response).map(value -> ward(value, provinceCode)).toList();
    }

    @Override
    public List<Province> snapshotProvinces() {
        return snapshot.stream().map(this::province).toList();
    }

    @Override
    public List<Ward> snapshotWards(int provinceCode) {
        return snapshot.stream()
                .filter(province -> province.code() == provinceCode)
                .flatMap(province -> province.wards().stream())
                .map(value -> ward(value, provinceCode))
                .toList();
    }

    private <T> T fetch(String dataType, String endpoint, Supplier<T> request) {
        int attempt = 1;
        while (true) {
            try {
                return request.get();
            } catch (ResourceAccessException exception) {
                boolean retrying = attempt <= maxRetries;
                Throwable rootCause = rootCause(exception);
                LOGGER.warn(
                        "Location upstream request failed: type={}, endpoint={}, exception={}, attempt={}, retrying={}",
                        dataType, endpoint, rootCause.getClass().getSimpleName(), attempt, retrying);
                if (!retrying) {
                    throw unavailable(dataType, endpoint, attempt, rootCause);
                }
                pause(dataType, endpoint, attempt, exception);
                attempt++;
            } catch (RestClientException exception) {
                Throwable rootCause = rootCause(exception);
                LOGGER.warn(
                        "Location upstream request failed without retry: type={}, endpoint={}, exception={}, attempt=1",
                        dataType, endpoint, rootCause.getClass().getSimpleName());
                throw unavailable(dataType, endpoint, 1, rootCause);
            }
        }
    }

    private void pause(String dataType, String endpoint, int attempt, ResourceAccessException exception) {
        try {
            Thread.sleep(retryDelay.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw unavailable(dataType, endpoint, attempt, exception);
        }
    }

    private SourceUnavailableException unavailable(
            String dataType, String endpoint, int attempt, Throwable exception) {
        return new SourceUnavailableException(
                "Location source request failed", exception, dataType, endpoint, attempt);
    }

    private Throwable rootCause(Throwable exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private List<ProvincePayload> readSnapshot(ObjectMapper objectMapper) {
        try (var input = new ClassPathResource(SNAPSHOT_PATH).getInputStream()) {
            return List.copyOf(objectMapper.readValue(input, new TypeReference<List<ProvincePayload>>() { }));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read bundled location snapshot", exception);
        }
    }

    private Province province(ProvincePayload value) {
        return new Province(value.code(), value.codename(), value.name(), value.divisionType());
    }

    private Ward ward(WardPayload value, int provinceCode) {
        return new Ward(value.code(), value.codename(), value.name(), value.divisionType(), provinceCode);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProvincePayload(
            int code,
            String codename,
            String name,
            @JsonProperty("division_type") String divisionType,
            List<WardPayload> wards) {

        ProvincePayload {
            wards = wards == null ? List.of() : List.copyOf(wards);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WardPayload(
            int code,
            String codename,
            String name,
            @JsonProperty("division_type") String divisionType) {
    }
}
