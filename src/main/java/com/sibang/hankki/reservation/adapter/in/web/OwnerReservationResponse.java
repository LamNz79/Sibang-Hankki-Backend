package com.sibang.hankki.reservation.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sibang.hankki.reservation.domain.model.Reservation;
import com.sibang.hankki.reservation.domain.model.ReservationProposal;
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
        Instant updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) AlternativeProposalResponse alternativeProposal) {

    static OwnerReservationResponse from(Reservation reservation) {
        return from(reservation, null);
    }

    static OwnerReservationResponse from(Reservation reservation, ReservationProposal proposal) {
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
                reservation.updatedAt(),
                AlternativeProposalResponse.from(proposal));
    }
}
