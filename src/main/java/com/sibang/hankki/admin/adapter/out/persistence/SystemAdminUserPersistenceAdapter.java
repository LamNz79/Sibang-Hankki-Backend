package com.sibang.hankki.admin.adapter.out.persistence;

import com.sibang.hankki.admin.adapter.out.persistence.repository.AdminRestaurantRepository;
import com.sibang.hankki.admin.adapter.out.persistence.repository.AdminUserRepository;
import com.sibang.hankki.admin.application.exception.AdminUserConflictException;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.AdminUserData;
import com.sibang.hankki.admin.application.port.out.SystemAdminUserPort;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantEntity;
import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class SystemAdminUserPersistenceAdapter implements SystemAdminUserPort {

    private final AdminUserRepository userRepository;
    private final AdminRestaurantRepository restaurantRepository;

    public SystemAdminUserPersistenceAdapter(
            AdminUserRepository userRepository, AdminRestaurantRepository restaurantRepository) {
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
    }

    @Override
    public Optional<AdminUserData> findById(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId).map(this::toData);
    }

    @Override
    public Optional<AdminUserData> findByIdForUpdate(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNullForUpdate(userId).map(this::toData);
    }

    @Override
    public boolean existsByUserid(String userid) {
        return userRepository.existsByUserid(userid);
    }

    @Override
    public AdminUserData create(String userid, String email, String passwordHash, String name, String phone) {
        UserEntity user = new UserEntity(userid, email, passwordHash, name);
        user.updateProfile(name, email, phone);
        try {
            return toData(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            throw new AdminUserConflictException("userid already exists");
        }
    }

    @Override
    public AdminUserData updateProfile(UUID userId, String email, String name, String phone) {
        UserEntity user = userRepository.getReferenceById(userId);
        user.updateProfile(name, email, phone);
        return toData(userRepository.saveAndFlush(user));
    }

    @Override
    public void updatePasswordHash(UUID userId, String passwordHash) {
        UserEntity user = userRepository.getReferenceById(userId);
        user.updatePasswordHash(passwordHash);
        userRepository.saveAndFlush(user);
    }

    private AdminUserData toData(UserEntity user) {
        String restaurantName = user.getRestaurantId() == null ? null
                : restaurantRepository.findById(user.getRestaurantId()).map(RestaurantEntity::getName).orElse(null);
        return new AdminUserData(
                user.getId(), user.getUserid(), user.getEmail(), user.getName(), user.getPhone(), user.getRole(),
                user.getStatus(), user.getRestaurantId(), restaurantName, user.getStatusChangedAt(),
                user.getCreatedAt(), user.getUpdatedAt());
    }
}
