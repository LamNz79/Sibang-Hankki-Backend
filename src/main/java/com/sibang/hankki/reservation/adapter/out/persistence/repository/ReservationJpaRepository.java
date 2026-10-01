package com.sibang.hankki.reservation.adapter.out.persistence.repository;

import com.sibang.hankki.reservation.adapter.out.persistence.entity.ReservationEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationJpaRepository extends JpaRepository<ReservationEntity, UUID> {

    Optional<ReservationEntity> findByReference(String reference);

    Optional<ReservationEntity> findByIdempotencyKey(String idempotencyKey);
}
