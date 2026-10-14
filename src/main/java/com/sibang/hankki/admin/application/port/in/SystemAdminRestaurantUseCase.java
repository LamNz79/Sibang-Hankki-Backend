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
            String address,
            String phone,
            String email,
            String timezone,
            String priceRange,
            Integer provinceCode,
            Integer wardCode) {
    }

    record UpdateRestaurantCommand(
            String name,
            String description,
            String cuisineType,
            String cuisineLabel,
            String address,
            String phone,
            String email,
            String timezone,
            String priceRange,
            Integer provinceCode,
            Integer wardCode) {
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
            Integer provinceCode,
            String provinceName,
            Integer wardCode,
            String wardName,
            String address,
            String phone,
            String email,
            String timezone,
            String priceRange,
            String approvalStatus,
            Instant createdAt,
            Instant updatedAt) {

        public AdminRestaurantDetail(
                UUID id, String slug, String name, String description, String cuisineType, String cuisineLabel,
                String citySlug, String area, String district, String address, String phone, String email,
                String timezone, String priceRange, String approvalStatus, Instant createdAt, Instant updatedAt) {
            this(id, slug, name, description, cuisineType, cuisineLabel, citySlug, area, district,
                    null, null, null, null, address, phone, email, timezone, priceRange, approvalStatus,
                    createdAt, updatedAt);
        }
    }
}
