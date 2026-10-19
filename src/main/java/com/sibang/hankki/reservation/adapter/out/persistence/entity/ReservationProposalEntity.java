package com.sibang.hankki.reservation.adapter.out.persistence.entity;

import com.sibang.hankki.reservation.domain.model.ReservationProposal;
import com.sibang.hankki.reservation.domain.model.ReservationProposalInitiator;
import com.sibang.hankki.reservation.domain.model.ReservationProposalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservation_proposals")
public class ReservationProposalEntity {

    @Id
    private UUID id;
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;
    @Column(name = "proposed_booking_slot_id", nullable = false)
    private UUID proposedBookingSlotId;
    @Column(name = "proposed_starts_at", nullable = false)
    private Instant proposedStartsAt;
    @Column(name = "proposed_party_size", nullable = false)
    private int proposedPartySize;
    @Column(name = "reservation_version", nullable = false)
    private int reservationVersion;
    @Column(columnDefinition = "text")
    private String message;
    @Column(name = "respond_by")
    private Instant respondBy;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationProposalStatus status;
    @Column(name = "created_by")
    private UUID createdBy;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationProposalInitiator initiator;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "responded_by")
    private UUID respondedBy;
    @Column(name = "responded_at")
    private Instant respondedAt;

    protected ReservationProposalEntity() {
    }

    public ReservationProposalEntity(ReservationProposal proposal) {
        this.id = proposal.id();
        updateFrom(proposal);
    }

    public void updateFrom(ReservationProposal proposal) {
        reservationId = proposal.reservationId();
        proposedBookingSlotId = proposal.proposedBookingSlotId();
        proposedStartsAt = proposal.proposedStartsAt();
        proposedPartySize = proposal.proposedPartySize();
        reservationVersion = proposal.reservationVersion();
        message = proposal.message();
        respondBy = proposal.respondBy();
        status = proposal.status();
        initiator = proposal.initiator();
        createdBy = proposal.createdBy();
        createdAt = proposal.createdAt();
        respondedBy = proposal.respondedBy();
        respondedAt = proposal.respondedAt();
    }

    public UUID getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public UUID getProposedBookingSlotId() { return proposedBookingSlotId; }
    public Instant getProposedStartsAt() { return proposedStartsAt; }
    public int getProposedPartySize() { return proposedPartySize; }
    public int getReservationVersion() { return reservationVersion; }
    public String getMessage() { return message; }
    public Instant getRespondBy() { return respondBy; }
    public ReservationProposalStatus getStatus() { return status; }
    public ReservationProposalInitiator getInitiator() { return initiator; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getRespondedBy() { return respondedBy; }
    public Instant getRespondedAt() { return respondedAt; }
}
