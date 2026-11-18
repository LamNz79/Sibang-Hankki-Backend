package com.sibang.hankki.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class VnpayPaymentServiceTest {

    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-11-05T06:00:00Z");

    @Mock private CustomerReservationUseCase guestReservations;
    @Mock private CustomerAccountReservationUseCase accountReservations;
    @Mock private PaymentOrderPort paymentOrders;
    @Mock private VnpayClient vnpayClient;
    @Mock private Reservation reservation;

    private VnpayPaymentService service;

    @BeforeEach
    void setUp() {
        VnpayProperties properties = new VnpayProperties();
        properties.setEnabled(true);
        properties.setTmnCode("TESTCODE");
        properties.setHashSecret("test-secret");
        service = new VnpayPaymentService(
                guestReservations, accountReservations, paymentOrders, vnpayClient, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsCheckoutOnlyAfterGuestReservationAccessIsVerified() {
        given(guestReservations.findById(RESERVATION_ID, "guest-token")).willReturn(reservation);
        given(reservation.status()).willReturn(ReservationStatus.CONFIRMED);
        given(paymentOrders.findPendingByReservationId(RESERVATION_ID)).willReturn(Optional.empty());
        given(paymentOrders.create(any(PaymentOrder.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(vnpayClient.checkoutUrl(any(PaymentOrder.class), any(), any())).willReturn("https://sandbox/checkout");

        var result = service.createCheckout(RESERVATION_ID, "guest-token", null, "127.0.0.1", "vn");

        assertThat(result.checkoutUrl()).isEqualTo("https://sandbox/checkout");
        assertThat(result.order().amount()).isEqualTo(100_000L);
        assertThat(result.order().status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.order().expiresAt()).isEqualTo(NOW.plusSeconds(600));
    }

    @Test
    void rejectsPaymentForUnconfirmedReservation() {
        given(guestReservations.findById(RESERVATION_ID, "guest-token")).willReturn(reservation);
        given(reservation.status()).willReturn(ReservationStatus.PENDING);

        assertThatThrownBy(() ->
                service.createCheckout(RESERVATION_ID, "guest-token", null, "127.0.0.1", "vn"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
        verify(paymentOrders, never()).create(any());
    }

    @Test
    void marksPaymentPaidOnlyForAValidSuccessfulIpn() {
        PaymentOrder order = order();
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", order.merchantReference());
        params.put("vnp_Amount", "10000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "14523321");
        given(vnpayClient.hasValidSignature(params)).willReturn(true);
        given(paymentOrders.findByMerchantReferenceForUpdate(order.merchantReference()))
                .willReturn(Optional.of(order));

        var result = service.processIpn(params);

        assertThat(result.rspCode()).isEqualTo("00");
        verify(paymentOrders).complete(
                order.id(), PaymentStatus.PAID, "14523321", "00", NOW, NOW);
    }

    private PaymentOrder order() {
        return new PaymentOrder(
                UUID.randomUUID(), RESERVATION_ID, "20261105060000abc123", 100_000L, "VND",
                PaymentStatus.PENDING, null, null, NOW.plusSeconds(600), null, NOW, NOW);
    }
}
