package com.sibang.hankki.admin.application;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.exception.AdminUserConflictException;
import com.sibang.hankki.admin.application.exception.InvalidAdminQueryException;
import com.sibang.hankki.admin.application.port.in.RestaurantAssignmentUseCase;
import com.sibang.hankki.admin.application.port.out.RestaurantAssignmentPort;
import com.sibang.hankki.admin.application.port.out.RestaurantAssignmentPort.AssignmentData;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.AdminUserData;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestaurantAssignmentService implements RestaurantAssignmentUseCase {

    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(10, 20, 50, 100);
    private static final Set<String> ASSIGNMENT_ROLES = Set.of("OWNER", "STAFF");

    private final RestaurantAssignmentPort assignmentPort;

    public RestaurantAssignmentService(RestaurantAssignmentPort assignmentPort) {
        this.assignmentPort = assignmentPort;
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentPage findAll(int page, int size, String query, String role, UUID restaurantId) {
        if (page < 0 || !ALLOWED_PAGE_SIZES.contains(size)) {
            throw new InvalidAdminQueryException("Invalid pagination");
        }
        String normalizedRole = role == null || role.isBlank() ? null : role.strip();
        if (normalizedRole != null && !ASSIGNMENT_ROLES.contains(normalizedRole)) {
            throw new InvalidAdminQueryException("role must be OWNER or STAFF");
        }
        var result = assignmentPort.findAll(page, size, likePattern(query), normalizedRole, restaurantId);
        return new AssignmentPage(
                result.items().stream().map(this::toAssignment).toList(),
                page, size, result.totalElements(), result.totalPages());
    }

    @Override
    @Transactional
    public Assignment create(AssignmentCommand command) {
        validate(command, true);
        String role = command.role().strip();
        AdminUserData user = findUser(command.userId());
        if (!"ACTIVE".equals(user.status())) {
            throw new AdminUserConflictException("Only ACTIVE users can be assigned");
        }
        if ("ADMIN".equals(user.role())) {
            throw new AdminUserConflictException("ADMIN cannot be assigned");
        }
        if (!"CUSTOMER".equals(user.role()) || user.restaurantId() != null) {
            throw new AdminUserConflictException("User already has a restaurant assignment");
        }
        findRestaurant(command.restaurantId());
        return toAssignment(assignmentPort.updateAccess(command.userId(), role, command.restaurantId()));
    }

    @Override
    @Transactional
    public Assignment update(UUID userId, AssignmentCommand command) {
        if (command == null) {
            throw new InvalidAdminQueryException("Request body is required");
        }
        AssignmentCommand normalized = new AssignmentCommand(userId, command.restaurantId(), command.role());
        validate(normalized, false);
        String role = command.role().strip();
        AdminUserData user = findUser(userId);
        if (!ASSIGNMENT_ROLES.contains(user.role()) || user.restaurantId() == null) {
            throw new AdminUserConflictException("User has no restaurant assignment");
        }
        findRestaurant(command.restaurantId());
        return toAssignment(assignmentPort.updateAccess(userId, role, command.restaurantId()));
    }

    @Override
    @Transactional
    public void remove(UUID userId) {
        AdminUserData user = findUser(userId);
        if (!ASSIGNMENT_ROLES.contains(user.role()) || user.restaurantId() == null) {
            throw new AdminUserConflictException("User has no restaurant assignment");
        }
        assignmentPort.updateAccess(userId, "CUSTOMER", null);
    }

    private void validate(AssignmentCommand command, boolean requireUserId) {
        if (command == null || requireUserId && command.userId() == null) {
            throw new InvalidAdminQueryException("userId is required");
        }
        if (command.restaurantId() == null) {
            throw new InvalidAdminQueryException("restaurantId is required");
        }
        if (command.role() == null || !ASSIGNMENT_ROLES.contains(command.role().strip())) {
            throw new InvalidAdminQueryException("role must be OWNER or STAFF");
        }
    }

    private AdminUserData findUser(UUID userId) {
        return assignmentPort.findUserForUpdate(userId)
                .orElseThrow(() -> new AdminResourceNotFoundException("User not found"));
    }

    private void findRestaurant(UUID restaurantId) {
        assignmentPort.findRestaurantForUpdate(restaurantId)
                .orElseThrow(() -> new AdminResourceNotFoundException("Restaurant not found"));
    }

    private String likePattern(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return "%" + query.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private Assignment toAssignment(AssignmentData data) {
        return new Assignment(
                data.userId(), data.userid(), data.name(), data.email(), data.userStatus(), data.role(),
                data.restaurantId(), data.restaurantSlug(), data.restaurantName(), data.restaurantStatus());
    }
}
