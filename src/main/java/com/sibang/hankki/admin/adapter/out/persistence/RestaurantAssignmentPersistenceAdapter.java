package com.sibang.hankki.admin.adapter.out.persistence;

import com.sibang.hankki.admin.adapter.out.persistence.repository.AdminRestaurantRepository;
import com.sibang.hankki.admin.adapter.out.persistence.repository.AdminUserRepository;
import com.sibang.hankki.admin.application.port.out.RestaurantAssignmentPort;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.AdminUserData;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantEntity;
import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class RestaurantAssignmentPersistenceAdapter implements RestaurantAssignmentPort {

    private static final Sort SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final AdminUserRepository userRepository;
    private final AdminRestaurantRepository restaurantRepository;

    public RestaurantAssignmentPersistenceAdapter(
            AdminUserRepository userRepository, AdminRestaurantRepository restaurantRepository) {
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
    }

    @Override
    public AssignmentPageData findAll(
            int page, int size, String queryPattern, String role, UUID restaurantId) {
        var result = userRepository.findAssignments(
                queryPattern, role, restaurantId, PageRequest.of(page, size, SORT));
        Map<UUID, RestaurantEntity> restaurants = restaurantRepository.findAllById(result.getContent().stream()
                        .map(UserEntity::getRestaurantId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(RestaurantEntity::getId, Function.identity()));
        return new AssignmentPageData(
                result.getContent().stream().map(user -> toAssignment(user, restaurants.get(user.getRestaurantId())))
                        .toList(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public Optional<AdminUserData> findUserForUpdate(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNullForUpdate(userId).map(this::toUserData);
    }

    @Override
    public Optional<RestaurantData> findRestaurantForUpdate(UUID restaurantId) {
        return restaurantRepository.findByIdAndDeletedAtIsNullForUpdate(restaurantId).map(this::toRestaurantData);
    }

    @Override
    public AssignmentData updateAccess(UUID userId, String role, UUID restaurantId) {
        UserEntity user = userRepository.getReferenceById(userId);
        user.updateRestaurantAccess(role, restaurantId);
        userRepository.saveAndFlush(user);
        if (restaurantId == null) {
            return new AssignmentData(
                    user.getId(), user.getUserid(), user.getName(), user.getEmail(), user.getStatus(), user.getRole(),
                    null, null, null, null);
        }
        RestaurantEntity restaurant = restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId).orElseThrow();
        return toAssignment(user, restaurant);
    }

    private AssignmentData toAssignment(UserEntity user, RestaurantEntity restaurant) {
        return new AssignmentData(
                user.getId(), user.getUserid(), user.getName(), user.getEmail(), user.getStatus(), user.getRole(),
                restaurant.getId(), restaurant.getSlug(), restaurant.getName(), restaurant.getApprovalStatus());
    }

    private AdminUserData toUserData(UserEntity user) {
        String restaurantName = user.getRestaurantId() == null ? null
                : restaurantRepository.findById(user.getRestaurantId()).map(RestaurantEntity::getName).orElse(null);
        return new AdminUserData(
                user.getId(), user.getUserid(), user.getEmail(), user.getName(), user.getPhone(), user.getRole(),
                user.getStatus(), user.getRestaurantId(), restaurantName, user.getStatusChangedAt(),
                user.getCreatedAt(), user.getUpdatedAt());
    }

    private RestaurantData toRestaurantData(RestaurantEntity restaurant) {
        return new RestaurantData(
                restaurant.getId(), restaurant.getSlug(), restaurant.getName(), restaurant.getApprovalStatus());
    }
}
