package com.sibang.hankki.reservation.adapter.out.persistence.repository;

import com.sibang.hankki.reservation.adapter.out.persistence.entity.ReservationEventEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationEventJpaRepository extends JpaRepository<ReservationEventEntity, UUID> {

    List<ReservationEventEntity> findByReservationIdOrderByCreatedAtAscIdAsc(UUID reservationId);
}
