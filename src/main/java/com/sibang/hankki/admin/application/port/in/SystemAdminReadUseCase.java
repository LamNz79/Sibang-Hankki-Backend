package com.sibang.hankki.admin.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SystemAdminReadUseCase {

    AdminUserPage findUsers(int page, int size, String query, String role, String status);

    AdminRestaurantPage findRestaurants(
            int page, int size, String query, String status, String citySlug, Integer provinceCode);

    default AdminRestaurantPage findRestaurants(
            int page, int size, String query, String status, String citySlug) {
        return findRestaurants(page, size, query, status, citySlug, null);
    }

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
            Integer provinceCode,
            String provinceName,
            String approvalStatus,
            Instant createdAt,
            Instant updatedAt) {

        public AdminRestaurant(
                UUID id, String slug, String name, String citySlug, String area, String district,
                String approvalStatus, Instant createdAt, Instant updatedAt) {
            this(id, slug, name, citySlug, area, district, null, null, approvalStatus, createdAt, updatedAt);
        }
    }

    record AdminRestaurantPage(
            List<AdminRestaurant> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
