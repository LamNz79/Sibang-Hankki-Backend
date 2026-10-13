package com.sibang.hankki.admin.application;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.exception.InvalidAdminQueryException;
import com.sibang.hankki.admin.application.exception.InvalidAdminStatusTransitionException;
import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase.AdminRestaurant;
import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase.AdminUser;
import com.sibang.hankki.admin.application.port.in.SystemAdminStatusUseCase;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.AdminRestaurantData;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.AdminUserData;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.RestaurantStatus;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.UserStatus;
import com.sibang.hankki.admin.application.port.out.SystemAdminStatusPort;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemAdminStatusService implements SystemAdminStatusUseCase {

    private static final EnumSet<UserStatus> MUTABLE_USER_STATUSES =
            EnumSet.of(UserStatus.ACTIVE, UserStatus.SUSPENDED);
    private static final EnumSet<RestaurantStatus> REQUESTABLE_RESTAURANT_STATUSES =
            EnumSet.of(RestaurantStatus.ACTIVE, RestaurantStatus.CHANGES_REQUESTED, RestaurantStatus.SUSPENDED);

    private final SystemAdminStatusPort statusPort;
    private final Clock clock;

    public SystemAdminStatusService(SystemAdminStatusPort statusPort, Clock clock) {
        this.statusPort = statusPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AdminUser updateUserStatus(UUID userId, String requestedStatus) {
        UserStatus target = parseUserStatus(requestedStatus);
        AdminUserData user = statusPort.findUserByIdForUpdate(userId)
                .orElseThrow(() -> new AdminResourceNotFoundException("User not found"));
        if ("ADMIN".equals(user.role())) {
            throw new InvalidAdminStatusTransitionException("ADMIN status cannot be changed");
        }

        UserStatus current = UserStatus.valueOf(user.status());
        if (!MUTABLE_USER_STATUSES.contains(current)) {
            throw new InvalidAdminStatusTransitionException("User status cannot be changed from " + current);
        }
        if (current == target) {
            return toUser(user);
        }

        Instant statusChangedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        return toUser(statusPort.updateUserStatus(userId, target, statusChangedAt));
    }

    @Override
    @Transactional
    public AdminRestaurant updateRestaurantStatus(UUID restaurantId, String requestedStatus) {
        RestaurantStatus target = parseRestaurantStatus(requestedStatus);
        AdminRestaurantData restaurant = statusPort.findRestaurantByIdForUpdate(restaurantId)
                .orElseThrow(() -> new AdminResourceNotFoundException("Restaurant not found"));
        RestaurantStatus current = RestaurantStatus.valueOf(restaurant.approvalStatus());
        if (current == target) {
            return toRestaurant(restaurant);
        }
        if (!isAllowed(current, target)) {
            throw new InvalidAdminStatusTransitionException(
                    "Restaurant status cannot change from " + current + " to " + target);
        }

        return toRestaurant(statusPort.updateRestaurantStatus(restaurantId, target));
    }

    private UserStatus parseUserStatus(String status) {
        UserStatus parsed = parse(status, UserStatus.class, "user status");
        if (!MUTABLE_USER_STATUSES.contains(parsed)) {
            throw new InvalidAdminQueryException("Invalid user status");
        }
        return parsed;
    }

    private RestaurantStatus parseRestaurantStatus(String status) {
        RestaurantStatus parsed = parse(status, RestaurantStatus.class, "restaurant status");
        if (!REQUESTABLE_RESTAURANT_STATUSES.contains(parsed)) {
            throw new InvalidAdminQueryException("Invalid restaurant status");
        }
        return parsed;
    }

    private <T extends Enum<T>> T parse(String value, Class<T> type, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidAdminQueryException("Invalid " + field);
        }
        try {
            return Enum.valueOf(type, value.strip());
        } catch (IllegalArgumentException exception) {
            throw new InvalidAdminQueryException("Invalid " + field);
        }
    }

    private boolean isAllowed(RestaurantStatus current, RestaurantStatus target) {
        return current == RestaurantStatus.PENDING
                        && (target == RestaurantStatus.ACTIVE || target == RestaurantStatus.CHANGES_REQUESTED)
                || current == RestaurantStatus.ACTIVE && target == RestaurantStatus.SUSPENDED
                || current == RestaurantStatus.SUSPENDED && target == RestaurantStatus.ACTIVE;
    }

    private AdminUser toUser(AdminUserData user) {
        return new AdminUser(
                user.id(), user.userid(), user.email(), user.name(), user.phone(), user.role(), user.status(),
                user.restaurantId(), user.statusChangedAt(), user.createdAt(), user.updatedAt());
    }

    private AdminRestaurant toRestaurant(AdminRestaurantData restaurant) {
        return new AdminRestaurant(
                restaurant.id(), restaurant.slug(), restaurant.name(), restaurant.citySlug(), restaurant.area(),
                restaurant.district(), restaurant.approvalStatus(), restaurant.createdAt(), restaurant.updatedAt());
    }
}
