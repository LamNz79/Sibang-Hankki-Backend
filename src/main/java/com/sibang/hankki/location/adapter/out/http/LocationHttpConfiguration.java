package com.sibang.hankki.location.adapter.out.http;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class LocationHttpConfiguration {

    @Bean("locationRestClient")
    RestClient locationRestClient(
            RestClient.Builder builder,
            @Value("${app.location-api.base-url:${APP_LOCATION_API_BASE_URL:https://provinces.open-api.vn/api/v2}}")
                    String baseUrl,
            @Value("${app.location-api.connect-timeout:${APP_LOCATION_API_CONNECT_TIMEOUT:3s}}")
                    Duration connectTimeout,
            @Value("${app.location-api.read-timeout:${APP_LOCATION_API_READ_TIMEOUT:10s}}")
                    Duration readTimeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
