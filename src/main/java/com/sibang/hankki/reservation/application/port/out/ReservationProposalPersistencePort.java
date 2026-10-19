package com.sibang.hankki.reservation.application.port.out;

import com.sibang.hankki.reservation.domain.model.ReservationProposal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ReservationProposalPersistencePort {

    ReservationProposal save(ReservationProposal proposal);

    Optional<ReservationProposal> findPendingByReservationId(UUID reservationId);

    Optional<ReservationProposal> findLatestByReservationId(UUID reservationId);

    Optional<ReservationProposal> findByIdAndReservationIdForUpdate(UUID id, UUID reservationId);

    void declinePendingByReservationId(UUID reservationId, UUID respondedBy, Instant respondedAt);
}
