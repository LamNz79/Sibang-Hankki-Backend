package com.sibang.hankki.location.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class VietnamProvincesHttpAdapterTest {

    @Test
    void mapsThirdPartyProvinceAndWardPayloadsToApplicationModels() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://locations.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var adapter = new VietnamProvincesHttpAdapter(builder.build());

        server.expect(once(), requestTo("https://locations.test/p/"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        [{"code":79,"codename":"ho_chi_minh","name":"Thành phố Hồ Chí Minh",
                          "division_type":"thành phố trung ương","phone_code":28,"wards":[]}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://locations.test/w/?province=79"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        [{"code":26734,"codename":"phuong_ben_nghe","name":"Phường Bến Nghé",
                          "division_type":"phường"}]
                        """, MediaType.APPLICATION_JSON));

        var provinces = adapter.fetchProvinces();
        assertThat(provinces).hasSize(1);
        assertThat(provinces.get(0).codename()).isEqualTo("ho_chi_minh");
        assertThat(adapter.fetchWards(79).get(0).provinceCode()).isEqualTo(79);
        server.verify();
    }
}
