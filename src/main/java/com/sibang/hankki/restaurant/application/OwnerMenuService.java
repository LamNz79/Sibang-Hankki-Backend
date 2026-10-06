package com.sibang.hankki.restaurant.application;

import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.exception.MenuResourceNotFoundException;
import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;
import com.sibang.hankki.restaurant.application.model.OwnerMenu;
import com.sibang.hankki.restaurant.application.model.OwnerMenuItemUpdate;
import com.sibang.hankki.restaurant.application.port.in.OwnerMenuUseCase;
import com.sibang.hankki.restaurant.application.port.out.OwnerMenuPort;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerMenuService implements OwnerMenuUseCase {

    private final OwnerMenuPort port;

    public OwnerMenuService(OwnerMenuPort port) {
        this.port = port;
    }

    @Override
    @Transactional(readOnly = true)
    public OwnerMenu get(UUID restaurantId) {
        return port.findAll(requireRestaurantId(restaurantId));
    }

    @Override
    @Transactional
    public OwnerMenu.Category createCategory(UUID restaurantId, UUID actorId, String name, int displayOrder) {
        validateCategory(name, displayOrder);
        return port.createCategory(requireRestaurantId(restaurantId), actorId, name.trim(), displayOrder);
    }

    @Override
    @Transactional
    public OwnerMenu.Category updateCategory(
            UUID restaurantId, UUID actorId, UUID id, String name, int displayOrder) {
        validateCategory(name, displayOrder);
        return port.updateCategory(requireRestaurantId(restaurantId), actorId, id, name.trim(), displayOrder)
                .orElseThrow(MenuResourceNotFoundException::new);
    }

    @Override
    @Transactional
    public void deleteCategory(UUID restaurantId, UUID actorId, UUID id) {
        if (!port.deleteCategory(requireRestaurantId(restaurantId), actorId, id)) {
            throw new MenuResourceNotFoundException();
        }
    }

    @Override
    @Transactional
    public OwnerMenu.Item createItem(UUID restaurantId, UUID actorId, OwnerMenuItemUpdate update) {
        OwnerMenuItemUpdate normalized = validateAndNormalize(update);
        return port.createItem(requireRestaurantId(restaurantId), actorId, normalized)
                .orElseThrow(MenuResourceNotFoundException::new);
    }

    @Override
    @Transactional
    public OwnerMenu.Item updateItem(
            UUID restaurantId, UUID actorId, UUID id, OwnerMenuItemUpdate update) {
        OwnerMenuItemUpdate normalized = validateAndNormalize(update);
        return port.updateItem(requireRestaurantId(restaurantId), actorId, id, normalized)
                .orElseThrow(MenuResourceNotFoundException::new);
    }

    @Override
    @Transactional
    public void deleteItem(UUID restaurantId, UUID actorId, UUID id) {
        if (!port.deleteItem(requireRestaurantId(restaurantId), actorId, id)) {
            throw new MenuResourceNotFoundException();
        }
    }

    private void validateCategory(String name, int displayOrder) {
        if (name == null || name.isBlank() || name.trim().length() > 50) {
            throw new InvalidBookingRequestException("category name is required and must be at most 50 characters");
        }
        if (displayOrder < 0) {
            throw new InvalidBookingRequestException("displayOrder must not be negative");
        }
    }

    private OwnerMenuItemUpdate validateAndNormalize(OwnerMenuItemUpdate update) {
        if (update == null || update.name() == null || update.name().isBlank()
                || update.name().trim().length() > 100) {
            throw new InvalidBookingRequestException("item name is required and must be at most 100 characters");
        }
        if (update.price() == null || update.price().signum() < 0 || update.price().scale() > 2) {
            throw new InvalidBookingRequestException("price must be non-negative with at most 2 decimal places");
        }
        if (update.categoryId() == null) {
            throw new InvalidBookingRequestException("categoryId is required");
        }
        String currency = update.currency() == null ? "VND" : update.currency().trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new InvalidBookingRequestException("currency must be a 3-letter code");
        }
        return new OwnerMenuItemUpdate(
                update.categoryId(), update.name().trim(), trimToNull(update.description()),
                update.price().setScale(2, RoundingMode.UNNECESSARY), currency,
                trimToNull(update.imageUrl()), update.available());
    }

    private UUID requireRestaurantId(UUID restaurantId) {
        if (restaurantId == null) {
            throw new RestaurantNotFoundException();
        }
        return restaurantId;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
