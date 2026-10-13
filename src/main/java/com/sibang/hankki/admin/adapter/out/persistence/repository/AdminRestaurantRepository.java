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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RestaurantEntity r where r.id = :id and r.deletedAt is null")
    Optional<RestaurantEntity> findByIdAndDeletedAtIsNullForUpdate(@Param("id") UUID id);
}
