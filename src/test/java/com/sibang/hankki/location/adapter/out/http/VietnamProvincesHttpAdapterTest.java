package com.sibang.hankki.location.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sibang.hankki.location.application.port.out.LocationCatalogPort.SourceUnavailableException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

class VietnamProvincesHttpAdapterTest {

    @Test
    void mapsThirdPartyProvinceAndWardPayloadsToApplicationModels() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://locations.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var adapter = adapter(builder, 2);

        server.expect(once(), requestTo("https://locations.test/p/"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        [{"code":79,"codename":"ho_chi_minh","name":"Thành phố Hồ Chí Minh",
                          "division_type":"thành phố trung ương","phone_code":28,"wards":[]}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://locations.test/w/?province=79"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        [{"code":27169,"codename":"phuong_dien_hong","name":"Phường Diên Hồng",
                          "division_type":"phường"}]
                        """, MediaType.APPLICATION_JSON));

        var provinces = adapter.fetchProvinces();
        assertThat(provinces).hasSize(1);
        assertThat(provinces.get(0).codename()).isEqualTo("ho_chi_minh");
        var ward = adapter.fetchWards(79).get(0);
        assertThat(ward.code()).isEqualTo(27169);
        assertThat(ward.name()).isEqualTo("Phường Diên Hồng");
        assertThat(ward.codename()).isEqualTo("phuong_dien_hong");
        assertThat(ward.divisionType()).isEqualTo("phường");
        assertThat(ward.provinceCode()).isEqualTo(79);
        server.verify();
    }

    @Test
    void retriesTemporaryConnectionFailureAndReturnsSuccessfulResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://locations.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var adapter = adapter(builder, 2);

        server.expect(once(), requestTo("https://locations.test/p/"))
                .andRespond(request -> {
                    throw new ResourceAccessException("timed out");
                });
        server.expect(once(), requestTo("https://locations.test/p/"))
                .andRespond(withSuccess("""
                        [{"code":79,"codename":"ho_chi_minh","name":"Thành phố Hồ Chí Minh",
                          "division_type":"thành phố trung ương"}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.fetchProvinces()).extracting(value -> value.code()).containsExactly(79);
        server.verify();
    }

    @Test
    void doesNotRetryHttpFourHundredResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://locations.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var adapter = adapter(builder, 2);

        server.expect(once(), requestTo("https://locations.test/p/"))
                .andRespond(withStatus(BAD_REQUEST));

        assertThatThrownBy(adapter::fetchProvinces).isInstanceOf(SourceUnavailableException.class);
        server.verify();
    }

    @Test
    void stopsAfterAtMostTwoRetries() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://locations.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var adapter = adapter(builder, 2);

        server.expect(org.springframework.test.web.client.ExpectedCount.times(3),
                        requestTo("https://locations.test/p/"))
                .andRespond(request -> {
                    throw new ResourceAccessException("timed out");
                });

        assertThatThrownBy(adapter::fetchProvinces).isInstanceOf(SourceUnavailableException.class);
        server.verify();
    }

    @Test
    void bundledSnapshotContainsTheCurrentCompleteCatalog() {
        var adapter = adapter(RestClient.builder().baseUrl("https://locations.test"), 0);

        assertThat(adapter.snapshotProvinces()).hasSize(34);
        assertThat(adapter.snapshotWards(79))
                .hasSizeGreaterThan(1)
                .anySatisfy(ward -> {
                    assertThat(ward.code()).isEqualTo(26740);
                    assertThat(ward.name()).isEqualTo("Phường Sài Gòn");
                    assertThat(ward.provinceCode()).isEqualTo(79);
                });
    }

    private VietnamProvincesHttpAdapter adapter(RestClient.Builder builder, int maxRetries) {
        return new VietnamProvincesHttpAdapter(
                builder.build(), new ObjectMapper(), maxRetries, Duration.ZERO);
    }
}
