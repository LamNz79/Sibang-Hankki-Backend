package com.sibang.hankki.admin.application.port.out;

import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.AdminRestaurantData;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.AdminUserData;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.RestaurantStatus;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.UserStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SystemAdminStatusPort {

    Optional<AdminUserData> findUserByIdForUpdate(UUID userId);

    AdminUserData updateUserStatus(UUID userId, UserStatus status, Instant statusChangedAt);

    Optional<AdminRestaurantData> findRestaurantByIdForUpdate(UUID restaurantId);

    AdminRestaurantData updateRestaurantStatus(UUID restaurantId, RestaurantStatus status);
}
