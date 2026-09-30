package com.sibang.hankki.restaurant.adapter.out.persistence.repository;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantBookingSettings;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantBookingSettingsRepository extends JpaRepository<RestaurantBookingSettings, UUID> {
}
