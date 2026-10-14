package com.sibang.hankki.admin.adapter.out.persistence;

import com.sibang.hankki.admin.adapter.out.persistence.repository.AdminRestaurantRepository;
import com.sibang.hankki.admin.adapter.out.persistence.repository.AdminUserRepository;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.AdminRestaurantData;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.AdminUserData;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.RestaurantStatus;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.UserStatus;
import com.sibang.hankki.admin.application.port.out.SystemAdminStatusPort;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantEntity;
import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SystemAdminStatusPersistenceAdapter implements SystemAdminStatusPort {

    private final AdminUserRepository userRepository;
    private final AdminRestaurantRepository restaurantRepository;

    public SystemAdminStatusPersistenceAdapter(
            AdminUserRepository userRepository,
            AdminRestaurantRepository restaurantRepository) {
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
    }

    @Override
    public Optional<AdminUserData> findUserByIdForUpdate(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNullForUpdate(userId).map(this::toUserData);
    }

    @Override
    public AdminUserData updateUserStatus(UUID userId, UserStatus status, Instant statusChangedAt) {
        UserEntity user = userRepository.getReferenceById(userId);
        user.updateStatus(status.name(), statusChangedAt);
        return toUserData(userRepository.saveAndFlush(user));
    }

    @Override
    public Optional<AdminRestaurantData> findRestaurantByIdForUpdate(UUID restaurantId) {
        return restaurantRepository.findByIdAndDeletedAtIsNullForUpdate(restaurantId).map(this::toRestaurantData);
    }

    @Override
    public AdminRestaurantData updateRestaurantStatus(UUID restaurantId, RestaurantStatus status) {
        RestaurantEntity restaurant = restaurantRepository.getReferenceById(restaurantId);
        restaurant.updateApprovalStatus(status.name());
        return toRestaurantData(restaurantRepository.saveAndFlush(restaurant));
    }

    private AdminUserData toUserData(UserEntity user) {
        return new AdminUserData(
                user.getId(), user.getUserid(), user.getEmail(), user.getName(), user.getPhone(), user.getRole(),
                user.getStatus(), user.getRestaurantId(), restaurantName(user), user.getStatusChangedAt(), user.getCreatedAt(),
                user.getUpdatedAt());
    }

    private String restaurantName(UserEntity user) {
        return user.getRestaurantId() == null ? null : restaurantRepository.findById(user.getRestaurantId())
                .map(RestaurantEntity::getName).orElse(null);
    }

    private AdminRestaurantData toRestaurantData(RestaurantEntity restaurant) {
        return new AdminRestaurantData(
                restaurant.getId(), restaurant.getSlug(), restaurant.getName(), restaurant.getCitySlug(),
                restaurant.getArea(), restaurant.getDistrict(), restaurant.getProvinceCode(),
                restaurant.getProvinceName(), restaurant.getApprovalStatus(),
                restaurant.getCreatedAt(), restaurant.getUpdatedAt());
    }
}
