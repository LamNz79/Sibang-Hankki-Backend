package com.sibang.hankki.admin.application.port.in;

import java.time.Instant;
import java.util.UUID;

public interface SystemAdminRestaurantUseCase {

    AdminRestaurantDetail findById(UUID restaurantId);

    AdminRestaurantDetail create(CreateRestaurantCommand command);

    AdminRestaurantDetail update(UUID restaurantId, UpdateRestaurantCommand command);

    void archive(UUID restaurantId);

    record CreateRestaurantCommand(
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
            String priceRange) {
    }

    record UpdateRestaurantCommand(
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

    record AdminRestaurantDetail(
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
}
