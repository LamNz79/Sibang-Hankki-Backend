package com.sibang.hankki.payment.domain.model;

import java.time.Instant;
import java.util.UUID;

public record PaymentOrder(
        UUID id,
        UUID reservationId,
        String merchantReference,
        long amount,
        String currency,
        PaymentStatus status,
        String providerTransactionId,
        String responseCode,
        Instant expiresAt,
        Instant paidAt,
        Instant createdAt,
        Instant updatedAt) {
}
