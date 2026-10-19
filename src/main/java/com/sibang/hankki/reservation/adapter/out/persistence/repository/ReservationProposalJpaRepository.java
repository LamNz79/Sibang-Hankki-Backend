package com.sibang.hankki.reservation.adapter.out.persistence.repository;

import com.sibang.hankki.reservation.adapter.out.persistence.entity.ReservationProposalEntity;
import com.sibang.hankki.reservation.domain.model.ReservationProposalStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationProposalJpaRepository extends JpaRepository<ReservationProposalEntity, UUID> {

    Optional<ReservationProposalEntity> findByReservationIdAndStatus(
            UUID reservationId, ReservationProposalStatus status);

    Optional<ReservationProposalEntity> findFirstByReservationIdOrderByCreatedAtDescIdDesc(UUID reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select proposal from ReservationProposalEntity proposal "
            + "where proposal.id = :id and proposal.reservationId = :reservationId")
    Optional<ReservationProposalEntity> findByIdAndReservationIdForUpdate(
            @Param("id") UUID id, @Param("reservationId") UUID reservationId);

    @Modifying
    @Query("update ReservationProposalEntity proposal set proposal.status = :status, "
            + "proposal.respondedBy = :respondedBy, proposal.respondedAt = :respondedAt "
            + "where proposal.reservationId = :reservationId and proposal.status = 'PENDING'")
    void declinePendingByReservationId(
            @Param("reservationId") UUID reservationId,
            @Param("status") ReservationProposalStatus status,
            @Param("respondedBy") UUID respondedBy,
            @Param("respondedAt") Instant respondedAt);
}
