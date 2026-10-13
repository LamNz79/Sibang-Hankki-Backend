package com.sibang.hankki.reservation.adapter.in.web;

import com.sibang.hankki.reservation.domain.model.ReservationProposal;
import java.time.Instant;
import java.util.UUID;

public record AlternativeProposalResponse(
        UUID id,
        Instant startsAt,
        String message,
        Instant respondBy,
        String status,
        Instant createdAt) {

    static AlternativeProposalResponse from(ReservationProposal proposal) {
        return proposal == null ? null : new AlternativeProposalResponse(
                proposal.id(), proposal.proposedStartsAt(), proposal.message(), proposal.respondBy(),
                proposal.status().name(), proposal.createdAt());
    }
}
