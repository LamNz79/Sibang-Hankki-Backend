package com.sibang.hankki.admin.adapter.out.persistence.repository;

import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminRestaurantRepository
        extends JpaRepository<RestaurantEntity, UUID>, JpaSpecificationExecutor<RestaurantEntity> {

    Optional<RestaurantEntity> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsBySlug(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RestaurantEntity r where r.id = :id and r.deletedAt is null")
    Optional<RestaurantEntity> findByIdAndDeletedAtIsNullForUpdate(@Param("id") UUID id);

    @Query(value = "select exists(select 1 from users where restaurant_id = :id and deleted_at is null)",
            nativeQuery = true)
    boolean hasNonDeletedUsers(@Param("id") UUID id);

    @Query(value = "select exists(select 1 from booking_slots where restaurant_id = :id)", nativeQuery = true)
    boolean hasBookingSlots(@Param("id") UUID id);

    @Query(value = "select exists(select 1 from reservations where restaurant_id = :id)", nativeQuery = true)
    boolean hasReservations(@Param("id") UUID id);
}
