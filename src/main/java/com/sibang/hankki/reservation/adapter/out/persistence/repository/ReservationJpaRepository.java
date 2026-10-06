package com.sibang.hankki.reservation.adapter.out.persistence.repository;

import com.sibang.hankki.reservation.adapter.out.persistence.entity.ReservationEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationJpaRepository extends JpaRepository<ReservationEntity, UUID> {

    Optional<ReservationEntity> findByReference(String reference);

    Optional<ReservationEntity> findByIdempotencyKey(String idempotencyKey);

    List<ReservationEntity> findAllByRestaurantIdOrderByStartsAtAscIdAsc(UUID restaurantId);

    Optional<ReservationEntity> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reservation from ReservationEntity reservation "
            + "where reservation.id = :id and reservation.restaurantId = :restaurantId")
    Optional<ReservationEntity> findByIdAndRestaurantIdForUpdate(
            @Param("id") UUID id, @Param("restaurantId") UUID restaurantId);
}
