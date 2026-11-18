package com.sibang.hankki.payment.application.port.out;

import com.sibang.hankki.payment.domain.model.PaymentOrder;
import com.sibang.hankki.payment.domain.model.PaymentStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PaymentOrderPort {

    Optional<PaymentOrder> findPendingByReservationId(UUID reservationId);

    Optional<PaymentOrder> findByMerchantReferenceForUpdate(String merchantReference);

    PaymentOrder create(PaymentOrder order);

    void complete(
            UUID id,
            PaymentStatus status,
            String providerTransactionId,
            String responseCode,
            Instant paidAt,
            Instant updatedAt);
}
