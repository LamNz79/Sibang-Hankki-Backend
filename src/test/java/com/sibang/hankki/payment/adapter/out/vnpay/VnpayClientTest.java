package com.sibang.hankki.payment.adapter.out.vnpay;

import static org.assertj.core.api.Assertions.assertThat;

import com.sibang.hankki.payment.config.VnpayProperties;
import com.sibang.hankki.payment.domain.model.PaymentOrder;
import com.sibang.hankki.payment.domain.model.PaymentStatus;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VnpayClientTest {

    private VnpayClient client;

    @BeforeEach
    void setUp() {
        VnpayProperties properties = new VnpayProperties();
        properties.setEnabled(true);
        properties.setTmnCode("TESTCODE");
        properties.setHashSecret("test-secret");
        properties.setReturnUrl("http://localhost:3000/reservations?payment=vnpay");
        client = new VnpayClient(properties);
    }

    @Test
    void createsAValidSandboxCheckoutSignature() {
        PaymentOrder order = new PaymentOrder(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("50000000-0000-0000-0000-000000000001"),
                "20261105060000abc123",
                100_000L,
                "VND",
                PaymentStatus.PENDING,
                null,
                null,
                Instant.parse("2026-11-05T06:10:00Z"),
                null,
                Instant.parse("2026-11-05T06:00:00Z"),
                Instant.parse("2026-11-05T06:00:00Z"));

        String checkoutUrl = client.checkoutUrl(order, "127.0.0.1", "vi");
        Map<String, String> params = decodedParams(checkoutUrl);

        assertThat(checkoutUrl).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(params)
                .containsEntry("vnp_Amount", "10000000")
                .containsEntry("vnp_CreateDate", "20261105130000")
                .containsEntry("vnp_ExpireDate", "20261105131000")
                .containsEntry("vnp_Locale", "vn");
        assertThat(client.hasValidSignature(params)).isTrue();

        params.put("vnp_Amount", "20000000");
        assertThat(client.hasValidSignature(params)).isFalse();
    }

    private Map<String, String> decodedParams(String url) {
        return Arrays.stream(URI.create(url).getRawQuery().split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }
}
