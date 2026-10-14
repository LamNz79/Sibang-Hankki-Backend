package com.sibang.hankki.admin.application.port.out;

import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.AdminUserData;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantAssignmentPort {

    AssignmentPageData findAll(int page, int size, String queryPattern, String role, UUID restaurantId);

    Optional<AdminUserData> findUserForUpdate(UUID userId);

    Optional<RestaurantData> findRestaurantForUpdate(UUID restaurantId);

    AssignmentData updateAccess(UUID userId, String role, UUID restaurantId);

    record RestaurantData(UUID id, String slug, String name, String status) {
    }

    record AssignmentData(
            UUID userId,
            String userid,
            String name,
            String email,
            String userStatus,
            String role,
            UUID restaurantId,
            String restaurantSlug,
            String restaurantName,
            String restaurantStatus) {
    }

    record AssignmentPageData(List<AssignmentData> items, long totalElements, int totalPages) {
    }
}
