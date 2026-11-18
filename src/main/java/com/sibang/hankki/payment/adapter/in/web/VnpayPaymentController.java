package com.sibang.hankki.payment.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sibang.hankki.auth.SessionUser;
import com.sibang.hankki.payment.application.VnpayPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/vnpay")
public class VnpayPaymentController {

    private static final String MANAGEMENT_TOKEN_HEADER = "X-Reservation-Management-Token";

    private final VnpayPaymentService service;

    public VnpayPaymentController(VnpayPaymentService service) {
        this.service = service;
    }

    @PostMapping("/reservations/{reservationId}")
    public CheckoutResponse createCheckout(
            @PathVariable UUID reservationId,
            @RequestHeader(value = MANAGEMENT_TOKEN_HEADER, required = false) String managementToken,
            @RequestParam(defaultValue = "vn") String locale,
            @AuthenticationPrincipal SessionUser sessionUser,
            HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        var checkout = service.createCheckout(
                reservationId,
                managementToken,
                sessionUser,
                forwardedFor == null ? request.getRemoteAddr() : forwardedFor,
                locale);
        var order = checkout.order();
        return new CheckoutResponse(
                order.id(), order.status().name(), order.amount(), order.currency(),
                order.expiresAt(), checkout.checkoutUrl());
    }

    @GetMapping("/ipn")
    public IpnResponse ipn(@RequestParam Map<String, String> params) {
        var result = service.processIpn(params);
        return new IpnResponse(result.rspCode(), result.message());
    }

    public record CheckoutResponse(
            UUID paymentId,
            String status,
            long amount,
            String currency,
            Instant expiresAt,
            String checkoutUrl) {
    }

    public record IpnResponse(
            @JsonProperty("RspCode") String rspCode,
            @JsonProperty("Message") String message) {
    }
}
