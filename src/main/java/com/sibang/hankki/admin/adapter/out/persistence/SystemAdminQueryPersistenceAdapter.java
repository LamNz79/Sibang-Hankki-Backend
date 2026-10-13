package com.sibang.hankki.admin.adapter.out.persistence;

import com.sibang.hankki.admin.adapter.out.persistence.repository.AdminRestaurantRepository;
import com.sibang.hankki.admin.adapter.out.persistence.repository.AdminUserRepository;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.AdminRestaurantData;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.AdminUserData;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.PageData;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.RestaurantStatus;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.UserRole;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.UserStatus;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantEntity;
import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class SystemAdminQueryPersistenceAdapter implements SystemAdminQueryPort {

    private static final Sort ADMIN_SORT = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final AdminUserRepository userRepository;
    private final AdminRestaurantRepository restaurantRepository;

    public SystemAdminQueryPersistenceAdapter(
            AdminUserRepository userRepository,
            AdminRestaurantRepository restaurantRepository) {
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
    }

    @Override
    public PageData<AdminUserData> findUsers(
            int page, int size, String query, UserRole role, UserStatus status) {
        Specification<UserEntity> specification = (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isNull(root.get("deletedAt")));
            if (query != null) {
                String pattern = likePattern(query);
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("userid")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), pattern, '\\')));
            }
            if (role != null) {
                predicates.add(criteriaBuilder.equal(root.get("role"), role.name()));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status.name()));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
        var result = userRepository.findAll(specification, PageRequest.of(page, size, ADMIN_SORT));
        return new PageData<>(
                result.getContent().stream().map(this::toUserData).toList(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public PageData<AdminRestaurantData> findRestaurants(
            int page, int size, String query, RestaurantStatus status, String citySlug) {
        Specification<RestaurantEntity> specification = (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isNull(root.get("deletedAt")));
            if (query != null) {
                String pattern = likePattern(query);
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("slug")), pattern, '\\')));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("approvalStatus"), status.name()));
            }
            if (citySlug != null) {
                predicates.add(criteriaBuilder.equal(root.get("citySlug"), citySlug));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
        var result = restaurantRepository.findAll(specification, PageRequest.of(page, size, ADMIN_SORT));
        return new PageData<>(
                result.getContent().stream().map(this::toRestaurantData).toList(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private String likePattern(String query) {
        return "%" + query.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }

    private AdminUserData toUserData(UserEntity user) {
        return new AdminUserData(
                user.getId(), user.getUserid(), user.getEmail(), user.getName(), user.getPhone(),
                user.getRole(), user.getStatus(), user.getRestaurantId(), user.getStatusChangedAt(),
                user.getCreatedAt(), user.getUpdatedAt());
    }

    private AdminRestaurantData toRestaurantData(RestaurantEntity restaurant) {
        return new AdminRestaurantData(
                restaurant.getId(), restaurant.getSlug(), restaurant.getName(), restaurant.getCitySlug(),
                restaurant.getArea(), restaurant.getDistrict(), restaurant.getApprovalStatus(),
                restaurant.getCreatedAt(), restaurant.getUpdatedAt());
    }
}
