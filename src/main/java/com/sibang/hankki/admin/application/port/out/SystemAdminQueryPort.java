package com.sibang.hankki.admin.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SystemAdminQueryPort {

    PageData<AdminUserData> findUsers(
            int page, int size, String query, UserRole role, UserStatus status);

    PageData<AdminRestaurantData> findRestaurants(
            int page, int size, String query, RestaurantStatus status, String citySlug);

    enum UserRole {
        CUSTOMER, OWNER, STAFF, ADMIN
    }

    enum UserStatus {
        ACTIVE, SUSPENDED, CLOSED
    }

    enum RestaurantStatus {
        DRAFT, PENDING, CHANGES_REQUESTED, ACTIVE, SUSPENDED
    }

    record PageData<T>(List<T> items, long totalElements, int totalPages) {
    }

    record AdminUserData(
            UUID id,
            String userid,
            String email,
            String name,
            String phone,
            String role,
            String status,
            UUID restaurantId,
            Instant statusChangedAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    record AdminRestaurantData(
            UUID id,
            String slug,
            String name,
            String citySlug,
            String area,
            String district,
            String approvalStatus,
            Instant createdAt,
            Instant updatedAt) {
    }
}
