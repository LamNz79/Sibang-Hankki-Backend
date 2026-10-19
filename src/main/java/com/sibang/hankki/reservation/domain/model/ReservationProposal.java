package com.sibang.hankki.reservation.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReservationProposal(
        UUID id,
        UUID reservationId,
        UUID proposedBookingSlotId,
        Instant proposedStartsAt,
        int proposedPartySize,
        int reservationVersion,
        String message,
        Instant respondBy,
        ReservationProposalStatus status,
        ReservationProposalInitiator initiator,
        UUID createdBy,
        Instant createdAt,
        UUID respondedBy,
        Instant respondedAt) {

    public ReservationProposal {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(reservationId, "reservationId is required");
        Objects.requireNonNull(proposedBookingSlotId, "proposedBookingSlotId is required");
        Objects.requireNonNull(proposedStartsAt, "proposedStartsAt is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(initiator, "initiator is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (initiator == ReservationProposalInitiator.RESTAURANT && createdBy == null) {
            throw new IllegalArgumentException("restaurant proposals require createdBy");
        }
        if (proposedPartySize <= 0) {
            throw new IllegalArgumentException("proposedPartySize must be positive");
        }
        if (reservationVersion < 0) {
            throw new IllegalArgumentException("reservationVersion must not be negative");
        }
    }

    public ReservationProposal(
            UUID id,
            UUID reservationId,
            UUID proposedBookingSlotId,
            Instant proposedStartsAt,
            int proposedPartySize,
            int reservationVersion,
            String message,
            Instant respondBy,
            ReservationProposalStatus status,
            UUID createdBy,
            Instant createdAt,
            UUID respondedBy,
            Instant respondedAt) {
        this(id, reservationId, proposedBookingSlotId, proposedStartsAt, proposedPartySize,
                reservationVersion, message, respondBy, status, ReservationProposalInitiator.RESTAURANT,
                createdBy, createdAt, respondedBy, respondedAt);
    }
}
