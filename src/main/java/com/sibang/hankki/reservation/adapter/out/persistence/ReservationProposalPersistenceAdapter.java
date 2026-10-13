package com.sibang.hankki.reservation.adapter.out.persistence;

import com.sibang.hankki.reservation.adapter.out.persistence.entity.ReservationProposalEntity;
import com.sibang.hankki.reservation.adapter.out.persistence.repository.ReservationProposalJpaRepository;
import com.sibang.hankki.reservation.application.port.out.ReservationProposalPersistencePort;
import com.sibang.hankki.reservation.domain.model.ReservationProposal;
import com.sibang.hankki.reservation.domain.model.ReservationProposalStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReservationProposalPersistenceAdapter implements ReservationProposalPersistencePort {

    private final ReservationProposalJpaRepository repository;

    public ReservationProposalPersistenceAdapter(ReservationProposalJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public ReservationProposal save(ReservationProposal proposal) {
        ReservationProposalEntity entity = repository.findById(proposal.id())
                .map(existing -> {
                    existing.updateFrom(proposal);
                    return existing;
                })
                .orElseGet(() -> new ReservationProposalEntity(proposal));
        return toProposal(repository.saveAndFlush(entity));
    }

    @Override
    public Optional<ReservationProposal> findPendingByReservationId(UUID reservationId) {
        return repository.findByReservationIdAndStatus(reservationId, ReservationProposalStatus.PENDING)
                .map(this::toProposal);
    }

    @Override
    public Optional<ReservationProposal> findLatestByReservationId(UUID reservationId) {
        return repository.findFirstByReservationIdOrderByCreatedAtDescIdDesc(reservationId).map(this::toProposal);
    }

    @Override
    public Optional<ReservationProposal> findByIdAndReservationIdForUpdate(UUID id, UUID reservationId) {
        return repository.findByIdAndReservationIdForUpdate(id, reservationId).map(this::toProposal);
    }

    private ReservationProposal toProposal(ReservationProposalEntity entity) {
        return new ReservationProposal(
                entity.getId(), entity.getReservationId(), entity.getProposedBookingSlotId(),
                entity.getProposedStartsAt(), entity.getProposedPartySize(), entity.getReservationVersion(),
                entity.getMessage(), entity.getRespondBy(), entity.getStatus(), entity.getCreatedBy(),
                entity.getCreatedAt(), entity.getRespondedBy(), entity.getRespondedAt());
    }
}
