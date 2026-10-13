package com.sibang.hankki.reservation.adapter.out.persistence.repository;

import com.sibang.hankki.reservation.adapter.out.persistence.entity.ReservationProposalEntity;
import com.sibang.hankki.reservation.domain.model.ReservationProposalStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
}
