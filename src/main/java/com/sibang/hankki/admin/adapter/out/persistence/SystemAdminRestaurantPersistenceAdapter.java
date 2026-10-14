package com.sibang.hankki.admin.adapter.out.persistence;

import com.sibang.hankki.admin.adapter.out.persistence.repository.AdminRestaurantRepository;
import com.sibang.hankki.admin.application.exception.AdminRestaurantConflictException;
import com.sibang.hankki.admin.application.port.out.SystemAdminRestaurantPort;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantEntity;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Component;

@Component
public class SystemAdminRestaurantPersistenceAdapter implements SystemAdminRestaurantPort {

    private final AdminRestaurantRepository repository;
    private final EntityManager entityManager;

    public SystemAdminRestaurantPersistenceAdapter(
            AdminRestaurantRepository repository,
            EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<RestaurantProfileData> findById(UUID restaurantId) {
        return repository.findByIdAndDeletedAtIsNull(restaurantId).map(this::toData);
    }

    @Override
    public Optional<RestaurantProfileData> findByIdForUpdate(UUID restaurantId) {
        return repository.findByIdAndDeletedAtIsNullForUpdate(restaurantId).map(this::toData);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return repository.existsBySlug(slug);
    }

    @Override
    public RestaurantProfileData create(RestaurantProfileData data) {
        RestaurantEntity restaurant = new RestaurantEntity(
                data.id(), data.slug(), data.name(), data.description(), data.cuisineType(), data.cuisineLabel(),
                data.citySlug(), data.area(), data.district(), data.address(), data.phone(), data.email(),
                data.timezone(), data.priceRange(), data.approvalStatus());
        try {
            entityManager.persist(restaurant);
            entityManager.flush();
            return toData(restaurant);
        } catch (RuntimeException exception) {
            if (hasConstraintViolation(exception)) {
                throw new AdminRestaurantConflictException("Restaurant slug already exists");
            }
            throw exception;
        }
    }

    @Override
    public RestaurantProfileData update(UUID restaurantId, RestaurantProfileUpdate update) {
        RestaurantEntity restaurant = repository.getReferenceById(restaurantId);
        restaurant.updateAdminProfile(
                update.name(), update.description(), update.cuisineType(), update.cuisineLabel(), update.citySlug(),
                update.area(), update.district(), update.address(), update.phone(), update.email(), update.timezone(),
                update.priceRange());
        return toData(repository.saveAndFlush(restaurant));
    }

    @Override
    public boolean hasNonDeletedUsers(UUID restaurantId) {
        return repository.hasNonDeletedUsers(restaurantId);
    }

    @Override
    public boolean hasReservations(UUID restaurantId) {
        return repository.hasReservations(restaurantId);
    }

    @Override
    public void archive(UUID restaurantId, Instant deletedAt) {
        RestaurantEntity restaurant = repository.getReferenceById(restaurantId);
        restaurant.archive(deletedAt);
        repository.saveAndFlush(restaurant);
    }

    private RestaurantProfileData toData(RestaurantEntity restaurant) {
        return new RestaurantProfileData(
                restaurant.getId(), restaurant.getSlug(), restaurant.getName(), restaurant.getDescription(),
                restaurant.getCuisineType(), restaurant.getCuisineLabel(), restaurant.getCitySlug(),
                restaurant.getArea(), restaurant.getDistrict(), restaurant.getAddress(), restaurant.getPhone(),
                restaurant.getEmail(), restaurant.getTimezone(), restaurant.getPriceRange(),
                restaurant.getApprovalStatus(), restaurant.getCreatedAt(), restaurant.getUpdatedAt());
    }

    private boolean hasConstraintViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException) {
                return true;
            }
        }
        return false;
    }
}
