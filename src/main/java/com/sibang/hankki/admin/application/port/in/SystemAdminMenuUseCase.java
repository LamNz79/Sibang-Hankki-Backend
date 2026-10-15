package com.sibang.hankki.admin.application.port.in;

import com.sibang.hankki.restaurant.application.model.OwnerMenu;
import com.sibang.hankki.restaurant.application.model.OwnerMenuItemUpdate;
import java.util.UUID;

public interface SystemAdminMenuUseCase {

    OwnerMenu get(UUID restaurantId);

    OwnerMenu.Category createCategory(
            UUID restaurantId, UUID actorId, String name, int displayOrder);

    OwnerMenu.Category updateCategory(
            UUID restaurantId, UUID actorId, UUID categoryId, String name, int displayOrder);

    void deleteCategory(UUID restaurantId, UUID actorId, UUID categoryId);

    OwnerMenu.Item createItem(UUID restaurantId, UUID actorId, OwnerMenuItemUpdate update);

    OwnerMenu.Item updateItem(
            UUID restaurantId, UUID actorId, UUID itemId, OwnerMenuItemUpdate update);

    void deleteItem(UUID restaurantId, UUID actorId, UUID itemId);
}
