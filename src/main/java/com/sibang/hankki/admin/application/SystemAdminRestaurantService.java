package com.sibang.hankki.admin.application;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.exception.AdminRestaurantConflictException;
import com.sibang.hankki.admin.application.exception.InvalidAdminQueryException;
import com.sibang.hankki.admin.application.port.in.SystemAdminRestaurantUseCase;
import com.sibang.hankki.admin.application.port.out.SystemAdminRestaurantPort;
import com.sibang.hankki.admin.application.port.out.SystemAdminRestaurantPort.RestaurantProfileData;
import com.sibang.hankki.admin.application.port.out.SystemAdminRestaurantPort.RestaurantProfileUpdate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemAdminRestaurantService implements SystemAdminRestaurantUseCase {

    private static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";
    private static final Pattern SLUG_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private final SystemAdminRestaurantPort restaurantPort;
    private final Clock clock;

    public SystemAdminRestaurantService(SystemAdminRestaurantPort restaurantPort, Clock clock) {
        this.restaurantPort = restaurantPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminRestaurantDetail findById(UUID restaurantId) {
        return toDetail(find(restaurantId));
    }

    @Override
    @Transactional
    public AdminRestaurantDetail create(CreateRestaurantCommand command) {
        if (command == null) {
            throw invalid("Request body is required");
        }
        String slug = required(command.slug(), "slug", 160);
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw invalid("slug must be lowercase kebab-case");
        }
        if (restaurantPort.existsBySlug(slug)) {
            throw new AdminRestaurantConflictException("Restaurant slug already exists");
        }

        RestaurantProfileUpdate profile = profile(
                command.name(), command.description(), command.cuisineType(), command.cuisineLabel(),
                command.citySlug(), command.area(), command.district(), command.address(), command.phone(),
                command.email(), command.timezone() == null || command.timezone().isBlank()
                        ? DEFAULT_TIMEZONE : command.timezone(),
                command.priceRange());
        return toDetail(restaurantPort.create(new RestaurantProfileData(
                UUID.randomUUID(), slug, profile.name(), profile.description(), profile.cuisineType(),
                profile.cuisineLabel(), profile.citySlug(), profile.area(), profile.district(), profile.address(),
                profile.phone(), profile.email(), profile.timezone(), profile.priceRange(), "DRAFT", null, null)));
    }

    @Override
    @Transactional
    public AdminRestaurantDetail update(UUID restaurantId, UpdateRestaurantCommand command) {
        if (command == null) {
            throw invalid("Request body is required");
        }
        findForUpdate(restaurantId);
        RestaurantProfileUpdate profile = profile(
                command.name(), command.description(), command.cuisineType(), command.cuisineLabel(),
                command.citySlug(), command.area(), command.district(), command.address(), command.phone(),
                command.email(), command.timezone(), command.priceRange());
        return toDetail(restaurantPort.update(restaurantId, profile));
    }

    @Override
    @Transactional
    public void archive(UUID restaurantId) {
        RestaurantProfileData restaurant = findForUpdate(restaurantId);
        if (!"DRAFT".equals(restaurant.approvalStatus())) {
            throw new AdminRestaurantConflictException("Only DRAFT restaurants can be archived");
        }
        if (restaurantPort.hasNonDeletedUsers(restaurantId) || restaurantPort.hasReservations(restaurantId)) {
            throw new AdminRestaurantConflictException("Restaurant is still referenced");
        }
        restaurantPort.archive(restaurantId, Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
    }

    private RestaurantProfileData find(UUID restaurantId) {
        if (restaurantId == null) {
            throw new AdminResourceNotFoundException("Restaurant not found");
        }
        return restaurantPort.findById(restaurantId)
                .orElseThrow(() -> new AdminResourceNotFoundException("Restaurant not found"));
    }

    private RestaurantProfileData findForUpdate(UUID restaurantId) {
        if (restaurantId == null) {
            throw new AdminResourceNotFoundException("Restaurant not found");
        }
        return restaurantPort.findByIdForUpdate(restaurantId)
                .orElseThrow(() -> new AdminResourceNotFoundException("Restaurant not found"));
    }

    private RestaurantProfileUpdate profile(
            String name,
            String description,
            String cuisineType,
            String cuisineLabel,
            String citySlug,
            String area,
            String district,
            String address,
            String phone,
            String email,
            String timezone,
            String priceRange) {
        String normalizedTimezone = required(timezone, "timezone", 64);
        try {
            ZoneId.of(normalizedTimezone);
        } catch (RuntimeException exception) {
            throw invalid("timezone is invalid");
        }
        return new RestaurantProfileUpdate(
                required(name, "name", 200), optional(description, Integer.MAX_VALUE), optional(cuisineType, 160),
                optional(cuisineLabel, 160), required(citySlug, "citySlug", 160), optional(area, 160),
                optional(district, 160), optional(address, Integer.MAX_VALUE), optional(phone, 30),
                optional(email, 320), normalizedTimezone, optional(priceRange, 40));
    }

    private String required(String value, String field, int maxLength) {
        String normalized = optional(value, maxLength);
        if (normalized == null) {
            throw invalid(field + " is required");
        }
        return normalized;
    }

    private String optional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw invalid("Value is too long");
        }
        return normalized;
    }

    private InvalidAdminQueryException invalid(String message) {
        return new InvalidAdminQueryException(message);
    }

    private AdminRestaurantDetail toDetail(RestaurantProfileData restaurant) {
        return new AdminRestaurantDetail(
                restaurant.id(), restaurant.slug(), restaurant.name(), restaurant.description(),
                restaurant.cuisineType(), restaurant.cuisineLabel(), restaurant.citySlug(), restaurant.area(),
                restaurant.district(), restaurant.address(), restaurant.phone(), restaurant.email(),
                restaurant.timezone(), restaurant.priceRange(), restaurant.approvalStatus(), restaurant.createdAt(),
                restaurant.updatedAt());
    }
}
