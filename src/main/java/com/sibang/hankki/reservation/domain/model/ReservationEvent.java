package com.sibang.hankki.reservation.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReservationEvent(
        UUID id,
        UUID reservationId,
        ReservationEventType eventType,
        UUID actorUserId,
        String commandId,
        String requestFingerprint,
        String metadata,
        Instant createdAt) {

    public ReservationEvent {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(reservationId, "reservationId is required");
        Objects.requireNonNull(eventType, "eventType is required");
    }
}
