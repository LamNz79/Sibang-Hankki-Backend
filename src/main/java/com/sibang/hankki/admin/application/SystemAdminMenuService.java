package com.sibang.hankki.admin.application;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.port.in.SystemAdminMenuUseCase;
import com.sibang.hankki.admin.application.port.out.SystemAdminRestaurantPort;
import com.sibang.hankki.restaurant.application.model.OwnerMenu;
import com.sibang.hankki.restaurant.application.model.OwnerMenuItemUpdate;
import com.sibang.hankki.restaurant.application.port.in.OwnerMenuUseCase;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SystemAdminMenuService implements SystemAdminMenuUseCase {

    private final SystemAdminRestaurantPort restaurantPort;
    private final OwnerMenuUseCase menuUseCase;

    public SystemAdminMenuService(
            SystemAdminRestaurantPort restaurantPort, OwnerMenuUseCase menuUseCase) {
        this.restaurantPort = restaurantPort;
        this.menuUseCase = menuUseCase;
    }

    @Override
    public OwnerMenu get(UUID restaurantId) {
        requireRestaurant(restaurantId);
        return menuUseCase.get(restaurantId);
    }

    @Override
    public OwnerMenu.Category createCategory(
            UUID restaurantId, UUID actorId, String name, int displayOrder) {
        requireRestaurant(restaurantId);
        return menuUseCase.createCategory(restaurantId, actorId, name, displayOrder);
    }

    @Override
    public OwnerMenu.Category updateCategory(
            UUID restaurantId, UUID actorId, UUID categoryId, String name, int displayOrder) {
        requireRestaurant(restaurantId);
        return menuUseCase.updateCategory(restaurantId, actorId, categoryId, name, displayOrder);
    }

    @Override
    public void deleteCategory(UUID restaurantId, UUID actorId, UUID categoryId) {
        requireRestaurant(restaurantId);
        menuUseCase.deleteCategory(restaurantId, actorId, categoryId);
    }

    @Override
    public OwnerMenu.Item createItem(
            UUID restaurantId, UUID actorId, OwnerMenuItemUpdate update) {
        requireRestaurant(restaurantId);
        return menuUseCase.createItem(restaurantId, actorId, update);
    }

    @Override
    public OwnerMenu.Item updateItem(
            UUID restaurantId, UUID actorId, UUID itemId, OwnerMenuItemUpdate update) {
        requireRestaurant(restaurantId);
        return menuUseCase.updateItem(restaurantId, actorId, itemId, update);
    }

    @Override
    public void deleteItem(UUID restaurantId, UUID actorId, UUID itemId) {
        requireRestaurant(restaurantId);
        menuUseCase.deleteItem(restaurantId, actorId, itemId);
    }

    private void requireRestaurant(UUID restaurantId) {
        if (restaurantId == null || restaurantPort.findById(restaurantId).isEmpty()) {
            throw new AdminResourceNotFoundException("Restaurant not found");
        }
    }
}
