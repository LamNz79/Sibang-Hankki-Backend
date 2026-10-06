package com.sibang.hankki.restaurant.application.port.out;

import com.sibang.hankki.restaurant.application.model.OwnerMenu;
import com.sibang.hankki.restaurant.application.model.OwnerMenuItemUpdate;
import java.util.Optional;
import java.util.UUID;

public interface OwnerMenuPort {
    OwnerMenu findAll(UUID restaurantId);

    OwnerMenu.Category createCategory(UUID restaurantId, UUID actorId, String name, int displayOrder);

    Optional<OwnerMenu.Category> updateCategory(
            UUID restaurantId, UUID actorId, UUID id, String name, int displayOrder);

    boolean deleteCategory(UUID restaurantId, UUID actorId, UUID id);

    Optional<OwnerMenu.Item> createItem(UUID restaurantId, UUID actorId, OwnerMenuItemUpdate update);

    Optional<OwnerMenu.Item> updateItem(UUID restaurantId, UUID actorId, UUID id, OwnerMenuItemUpdate update);

    boolean deleteItem(UUID restaurantId, UUID actorId, UUID id);
}
