package com.sibang.hankki.payment.adapter.out.vnpay;

import com.sibang.hankki.payment.config.VnpayProperties;
import com.sibang.hankki.payment.domain.model.PaymentOrder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class VnpayClient {

    private static final DateTimeFormatter VNPAY_DATE =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final VnpayProperties properties;

    public VnpayClient(VnpayProperties properties) {
        this.properties = properties;
    }

    public String checkoutUrl(PaymentOrder order, String clientIp, String locale) {
        properties.validateForUse();
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", properties.getTmnCode());
        params.put("vnp_Amount", Long.toString(order.amount() * 100));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", order.merchantReference());
        params.put("vnp_OrderInfo", "Sibang Hankki deposit " + order.merchantReference());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "en".equalsIgnoreCase(locale) ? "en" : "vn");
        params.put("vnp_ReturnUrl", properties.getReturnUrl());
        params.put("vnp_IpAddr", safeIp(clientIp));
        params.put("vnp_CreateDate", VNPAY_DATE.format(order.createdAt()));
        params.put("vnp_ExpireDate", VNPAY_DATE.format(order.expiresAt()));

        String query = canonicalQuery(params);
        return properties.getPaymentUrl() + "?" + query + "&vnp_SecureHash=" + hmacSha512(query);
    }

    public boolean hasValidSignature(Map<String, String> incoming) {
        String supplied = incoming.get("vnp_SecureHash");
        if (supplied == null || supplied.isBlank()) {
            return false;
        }
        Map<String, String> signed = new TreeMap<>(incoming);
        signed.remove("vnp_SecureHash");
        signed.remove("vnp_SecureHashType");
        return constantTimeEquals(hmacSha512(canonicalQuery(signed)), supplied);
    }

    private String canonicalQuery(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String hmacSha512(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(properties.getHashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign VNPAY request", exception);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] left = expected.toLowerCase().getBytes(StandardCharsets.US_ASCII);
        byte[] right = actual.toLowerCase().getBytes(StandardCharsets.US_ASCII);
        return left.length == right.length && java.security.MessageDigest.isEqual(left, right);
    }

    private String safeIp(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp.split(",", 2)[0].strip();
    }
}
