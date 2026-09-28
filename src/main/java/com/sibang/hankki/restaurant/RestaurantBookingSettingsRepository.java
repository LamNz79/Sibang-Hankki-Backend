package com.sibang.hankki.restaurant;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RestaurantBookingSettingsRepository extends JpaRepository<RestaurantBookingSettings, UUID> {
}
