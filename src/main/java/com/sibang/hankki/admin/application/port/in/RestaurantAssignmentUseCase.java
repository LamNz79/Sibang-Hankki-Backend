package com.sibang.hankki.admin.application.port.in;

import java.util.List;
import java.util.UUID;

public interface RestaurantAssignmentUseCase {

    AssignmentPage findAll(int page, int size, String query, String role, UUID restaurantId);

    Assignment create(AssignmentCommand command);

    Assignment update(UUID userId, AssignmentCommand command);

    void remove(UUID userId);

    record AssignmentCommand(UUID userId, UUID restaurantId, String role) {
    }

    record Assignment(
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

    record AssignmentPage(List<Assignment> items, int page, int size, long totalElements, int totalPages) {
    }
}
