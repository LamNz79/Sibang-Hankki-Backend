package com.sibang.hankki.admin.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SystemAdminReadUseCase {

    AdminUserPage findUsers(int page, int size, String query, String role, String status);

    AdminRestaurantPage findRestaurants(
            int page, int size, String query, String status, String citySlug);

    record AdminUser(
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

    record AdminUserPage(
            List<AdminUser> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    record AdminRestaurant(
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

    record AdminRestaurantPage(
            List<AdminRestaurant> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
