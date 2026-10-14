package com.sibang.hankki.admin.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SystemAdminRestaurantPort {

    Optional<RestaurantProfileData> findById(UUID restaurantId);

    Optional<RestaurantProfileData> findByIdForUpdate(UUID restaurantId);

    boolean existsBySlug(String slug);

    RestaurantProfileData create(RestaurantProfileData restaurant);

    RestaurantProfileData update(UUID restaurantId, RestaurantProfileUpdate update);

    boolean hasNonDeletedUsers(UUID restaurantId);

    boolean hasReservations(UUID restaurantId);

    void archive(UUID restaurantId, Instant deletedAt);

    record RestaurantProfileData(
            UUID id,
            String slug,
            String name,
            String description,
            String cuisineType,
            String cuisineLabel,
            String citySlug,
            String area,
            String district,
            String address,
            String phone,
            String email,
            String timezone,
            String priceRange,
            String approvalStatus,
            Instant createdAt,
            Instant updatedAt) {
    }

    record RestaurantProfileUpdate(
            String name,
            String description,
            String cuisineType,
            String cuisineLabel,
            String citySlug,
            String area,
            String district,
            String address,
            String phone,
            String email,
            String timezone,
            String priceRange) {
    }
}
