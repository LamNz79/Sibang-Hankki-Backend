package com.sibang.hankki.admin.application;

import com.sibang.hankki.admin.application.exception.InvalidAdminQueryException;
import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.RestaurantStatus;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.UserRole;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.UserStatus;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemAdminReadService implements SystemAdminReadUseCase {

    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(10, 20, 50, 100);

    private final SystemAdminQueryPort queryPort;

    public SystemAdminReadService(SystemAdminQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserPage findUsers(int page, int size, String query, String role, String status) {
        validatePage(page, size);
        var result = queryPort.findUsers(
                page,
                size,
                normalize(query),
                parse(role, UserRole.class, "role"),
                parse(status, UserStatus.class, "status"));
        return new AdminUserPage(
                result.items().stream().map(user -> new AdminUser(
                        user.id(), user.userid(), user.email(), user.name(), user.phone(),
                        user.role(), user.status(), user.restaurantId(), user.statusChangedAt(),
                        user.createdAt(), user.updatedAt())).toList(),
                page,
                size,
                result.totalElements(),
                result.totalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminRestaurantPage findRestaurants(
            int page, int size, String query, String status, String citySlug) {
        validatePage(page, size);
        var result = queryPort.findRestaurants(
                page,
                size,
                normalize(query),
                parse(status, RestaurantStatus.class, "status"),
                normalize(citySlug));
        return new AdminRestaurantPage(
                result.items().stream().map(restaurant -> new AdminRestaurant(
                        restaurant.id(), restaurant.slug(), restaurant.name(), restaurant.citySlug(),
                        restaurant.area(), restaurant.district(), restaurant.approvalStatus(),
                        restaurant.createdAt(), restaurant.updatedAt())).toList(),
                page,
                size,
                result.totalElements(),
                result.totalPages());
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new InvalidAdminQueryException("page must not be negative");
        }
        if (!ALLOWED_PAGE_SIZES.contains(size)) {
            throw new InvalidAdminQueryException("size must be one of 10, 20, 50, or 100");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private <T extends Enum<T>> T parse(String value, Class<T> type, String field) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.strip());
        } catch (IllegalArgumentException exception) {
            throw new InvalidAdminQueryException("Invalid " + field);
        }
    }
}
