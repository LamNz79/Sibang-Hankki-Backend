package com.sibang.hankki.reservation.application.port.in;

import com.sibang.hankki.reservation.domain.model.Reservation;
import com.sibang.hankki.reservation.domain.model.ReservationProposal;
import java.util.Optional;
import java.util.UUID;

public interface ReservationProposalUseCase {

    ProposalResult create(OwnerProposalCommand command);

    ProposalResult acceptForAccount(UUID reservationId, UUID proposalId, UUID customerId);

    ProposalResult declineForAccount(UUID reservationId, UUID proposalId, UUID customerId);

    ProposalResult acceptForGuest(UUID reservationId, UUID proposalId, String managementToken);

    ProposalResult declineForGuest(UUID reservationId, UUID proposalId, String managementToken);

    Reservation requestAnotherTimeForAccount(
            UUID reservationId,
            UUID proposalId,
            UUID customerId,
            String requestedDate,
            String requestedTime);

    Reservation requestAnotherTimeForGuest(
            UUID reservationId,
            UUID proposalId,
            String managementToken,
            String requestedDate,
            String requestedTime);

    ProposalResult requestChangeForAccount(
            UUID reservationId,
            UUID customerId,
            String requestedDate,
            String requestedTime,
            String message);

    ProposalResult requestChangeForGuest(
            UUID reservationId,
            String managementToken,
            String requestedDate,
            String requestedTime,
            String message);

    ProposalResult acceptChangeRequest(
            UUID reservationId, UUID requestId, UUID restaurantId, UUID actorUserId);

    ProposalResult declineChangeRequest(
            UUID reservationId, UUID requestId, UUID restaurantId, UUID actorUserId);

    Optional<ReservationProposal> findLatest(UUID reservationId);

    record OwnerProposalCommand(
            UUID reservationId,
            UUID restaurantId,
            UUID actorUserId,
            String proposedDate,
            String proposedTime,
            String message,
            String respondBy) {
    }

    record ProposalResult(Reservation reservation, ReservationProposal proposal) {
    }
}
