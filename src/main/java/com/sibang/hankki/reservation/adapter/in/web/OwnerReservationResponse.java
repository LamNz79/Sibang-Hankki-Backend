package com.sibang.hankki.reservation.adapter.in.web;

import com.sibang.hankki.reservation.domain.model.Reservation;
import java.time.Instant;
import java.util.UUID;

public record OwnerReservationResponse(
        UUID id,
        String reference,
        String customerName,
        String customerEmail,
        String customerPhone,
        Instant startsAt,
        Instant endsAt,
        int partySize,
        String status,
        String visitStatus,
        String specialRequest,
        String preOrderNote,
        Instant createdAt,
        Instant updatedAt) {

    static OwnerReservationResponse from(Reservation reservation) {
        return new OwnerReservationResponse(
                reservation.id(),
                reservation.reference(),
                reservation.customerName(),
                reservation.customerEmail(),
                reservation.customerPhone(),
                reservation.startsAt(),
                reservation.endsAt(),
                reservation.partySize(),
                reservation.status().name(),
                reservation.visitStatus() == null ? null : reservation.visitStatus().name(),
                reservation.specialRequest(),
                reservation.preOrderNote(),
                reservation.createdAt(),
                reservation.updatedAt());
    }
}
