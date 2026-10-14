package com.sibang.hankki.location.adapter.out.http;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase.Province;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase.Ward;
import com.sibang.hankki.location.application.port.out.LocationCatalogPort;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class VietnamProvincesHttpAdapter implements LocationCatalogPort {

    private final RestClient restClient;

    public VietnamProvincesHttpAdapter(@Qualifier("locationRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<Province> fetchProvinces() {
        try {
            ProvincePayload[] response = restClient.get().uri("/p/").retrieve().body(ProvincePayload[].class);
            return response == null ? List.of() : Arrays.stream(response)
                    .map(value -> new Province(value.code(), value.codename(), value.name(), value.divisionType()))
                    .toList();
        } catch (RestClientException exception) {
            throw new SourceUnavailableException("Province source request failed", exception);
        }
    }

    @Override
    public List<Ward> fetchWards(int provinceCode) {
        try {
            WardPayload[] response = restClient.get()
                    .uri(builder -> builder.path("/w/").queryParam("province", provinceCode).build())
                    .retrieve()
                    .body(WardPayload[].class);
            return response == null ? List.of() : Arrays.stream(response)
                    .map(value -> new Ward(
                            value.code(), value.codename(), value.name(), value.divisionType(), provinceCode))
                    .toList();
        } catch (RestClientException exception) {
            throw new SourceUnavailableException("Ward source request failed", exception);
        }
    }

    private record ProvincePayload(
            int code,
            String codename,
            String name,
            @JsonProperty("division_type") String divisionType) {
    }

    private record WardPayload(
            int code,
            String codename,
            String name,
            @JsonProperty("division_type") String divisionType) {
    }
}
