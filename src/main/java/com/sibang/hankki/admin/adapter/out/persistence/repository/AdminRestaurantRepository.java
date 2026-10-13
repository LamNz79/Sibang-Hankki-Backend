package com.sibang.hankki.admin.adapter.out.persistence.repository;

import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AdminRestaurantRepository
        extends JpaRepository<RestaurantEntity, UUID>, JpaSpecificationExecutor<RestaurantEntity> {
}
