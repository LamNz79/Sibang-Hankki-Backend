package com.sibang.hankki.reservation.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Reservation(
        UUID id,
        String reference,
        String idempotencyKey,
        String requestFingerprint,
        UUID restaurantId,
        UUID bookingSlotId,
        UUID customerId,
        String customerName,
        String customerEmail,
        String customerPhone,
        Instant startsAt,
        Instant endsAt,
        int partySize,
        ReservationStatus status,
        boolean capacityOverride,
        VisitStatus visitStatus,
        String specialRequest,
        String preOrderNote,
        String managementTokenHash,
        String checkInTokenHash,
        Instant checkedInAt,
        UUID checkedInBy,
        int version,
        Instant createdAt,
        Instant updatedAt) {

    public Reservation {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(reference, "reference is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        Objects.requireNonNull(requestFingerprint, "requestFingerprint is required");
        Objects.requireNonNull(restaurantId, "restaurantId is required");
        Objects.requireNonNull(customerName, "customerName is required");
        Objects.requireNonNull(customerPhone, "customerPhone is required");
        Objects.requireNonNull(startsAt, "startsAt is required");
        Objects.requireNonNull(endsAt, "endsAt is required");
        Objects.requireNonNull(status, "status is required");
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("endsAt must be after startsAt");
        }
        if (partySize <= 0) {
            throw new IllegalArgumentException("partySize must be positive");
        }
        if (status == ReservationStatus.CONFIRMED && visitStatus == null) {
            throw new IllegalArgumentException("confirmed reservations require visitStatus");
        }
        if (status != ReservationStatus.CONFIRMED && visitStatus != null) {
            throw new IllegalArgumentException("only confirmed reservations may have visitStatus");
        }
        if (capacityOverride && status != ReservationStatus.CONFIRMED) {
            throw new IllegalArgumentException("capacityOverride requires a confirmed reservation");
        }
        if (managementTokenHash != null && managementTokenHash.length() != 64) {
            throw new IllegalArgumentException("managementTokenHash must be a SHA-256 hex value");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
