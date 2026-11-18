package com.sibang.hankki.payment.application;

import com.sibang.hankki.auth.SessionUser;
import com.sibang.hankki.payment.adapter.out.vnpay.VnpayClient;
import com.sibang.hankki.payment.application.port.out.PaymentOrderPort;
import com.sibang.hankki.payment.config.VnpayProperties;
import com.sibang.hankki.payment.domain.model.PaymentOrder;
import com.sibang.hankki.payment.domain.model.PaymentStatus;
import com.sibang.hankki.reservation.application.port.in.CustomerAccountReservationUseCase;
import com.sibang.hankki.reservation.application.port.in.CustomerReservationUseCase;
import com.sibang.hankki.reservation.domain.model.Reservation;
import com.sibang.hankki.reservation.domain.model.ReservationStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VnpayPaymentService {

    private static final DateTimeFormatter REFERENCE_DATE =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final CustomerReservationUseCase guestReservations;
    private final CustomerAccountReservationUseCase accountReservations;
    private final PaymentOrderPort paymentOrders;
    private final VnpayClient vnpayClient;
    private final VnpayProperties properties;
    private final Clock clock;

    public VnpayPaymentService(
            CustomerReservationUseCase guestReservations,
            CustomerAccountReservationUseCase accountReservations,
            PaymentOrderPort paymentOrders,
            VnpayClient vnpayClient,
            VnpayProperties properties,
            Clock clock) {
        this.guestReservations = guestReservations;
        this.accountReservations = accountReservations;
        this.paymentOrders = paymentOrders;
        this.vnpayClient = vnpayClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Checkout createCheckout(
            UUID reservationId,
            String managementToken,
            SessionUser sessionUser,
            String clientIp,
            String locale) {
        properties.validateForUse();
        Reservation reservation = accessibleReservation(reservationId, managementToken, sessionUser);
        if (reservation.status() != ReservationStatus.CONFIRMED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only confirmed reservations can be paid");
        }

        Instant now = clock.instant();
        PaymentOrder order = paymentOrders.findPendingByReservationId(reservationId)
                .filter(existing -> existing.expiresAt().isAfter(now))
                .orElseGet(() -> createOrder(reservationId, now));
        return new Checkout(order, vnpayClient.checkoutUrl(order, clientIp, locale));
    }

    @Transactional
    public IpnResult processIpn(Map<String, String> params) {
        try {
            properties.validateForUse();
            if (!vnpayClient.hasValidSignature(params)) {
                return new IpnResult("97", "Invalid Checksum");
            }

            String reference = params.get("vnp_TxnRef");
            PaymentOrder order = paymentOrders.findByMerchantReferenceForUpdate(reference).orElse(null);
            if (order == null) {
                return new IpnResult("01", "Order not Found");
            }
            if (order.status() != PaymentStatus.PENDING) {
                return new IpnResult("02", "Order already confirmed");
            }

            long providerAmount = Long.parseLong(params.getOrDefault("vnp_Amount", "-1"));
            if (providerAmount != order.amount() * 100) {
                return new IpnResult("04", "Invalid Amount");
            }

            String responseCode = params.get("vnp_ResponseCode");
            String transactionStatus = params.get("vnp_TransactionStatus");
            boolean paid = "00".equals(responseCode) && "00".equals(transactionStatus);
            Instant now = clock.instant();
            paymentOrders.complete(
                    order.id(),
                    paid ? PaymentStatus.PAID : PaymentStatus.FAILED,
                    params.get("vnp_TransactionNo"),
                    responseCode,
                    paid ? now : null,
                    now);
            return new IpnResult("00", "Confirm Success");
        } catch (RuntimeException exception) {
            return new IpnResult("99", "Unknown error");
        }
    }

    private Reservation accessibleReservation(
            UUID reservationId, String managementToken, SessionUser sessionUser) {
        if (sessionUser == null) {
            return guestReservations.findById(reservationId, managementToken);
        }
        if (!"CUSTOMER".equals(sessionUser.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return accountReservations.findAccountReservation(reservationId, sessionUser.id()).reservation();
    }

    private PaymentOrder createOrder(UUID reservationId, Instant now) {
        paymentOrders.findPendingByReservationId(reservationId)
                .filter(existing -> !existing.expiresAt().isAfter(now))
                .ifPresent(expired -> paymentOrders.complete(
                        expired.id(), PaymentStatus.EXPIRED, null, null, null, now));

        PaymentOrder order = new PaymentOrder(
                UUID.randomUUID(),
                reservationId,
                REFERENCE_DATE.format(now) + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                properties.getDepositAmount(),
                "VND",
                PaymentStatus.PENDING,
                null,
                null,
                now.plusSeconds(properties.getExpiryMinutes() * 60L),
                null,
                now,
                now);
        return paymentOrders.create(order);
    }

    public record Checkout(PaymentOrder order, String checkoutUrl) {
    }

    public record IpnResult(String rspCode, String message) {
    }
}
