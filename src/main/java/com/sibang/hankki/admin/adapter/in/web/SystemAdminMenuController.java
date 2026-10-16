package com.sibang.hankki.admin.adapter.in.web;

import com.sibang.hankki.admin.application.port.in.SystemAdminMenuUseCase;
import com.sibang.hankki.auth.SessionUser;
import com.sibang.hankki.restaurant.adapter.in.web.OwnerMenuController.CategoryRequest;
import com.sibang.hankki.restaurant.adapter.in.web.OwnerMenuController.ItemRequest;
import com.sibang.hankki.restaurant.application.model.OwnerMenu;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/restaurants/{restaurantId}/menu")
public class SystemAdminMenuController {

    private final SystemAdminMenuUseCase useCase;

    public SystemAdminMenuController(SystemAdminMenuUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public OwnerMenu get(@PathVariable UUID restaurantId) {
        return useCase.get(restaurantId);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerMenu.Category createCategory(
            @PathVariable UUID restaurantId,
            @RequestBody CategoryRequest request,
            @AuthenticationPrincipal SessionUser user) {
        return useCase.createCategory(
                restaurantId, user.id(), request.name(), request.displayOrder());
    }

    @PutMapping("/categories/{categoryId}")
    public OwnerMenu.Category updateCategory(
            @PathVariable UUID restaurantId,
            @PathVariable UUID categoryId,
            @RequestBody CategoryRequest request,
            @AuthenticationPrincipal SessionUser user) {
        return useCase.updateCategory(
                restaurantId, user.id(), categoryId, request.name(), request.displayOrder());
    }

    @DeleteMapping("/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(
            @PathVariable UUID restaurantId,
            @PathVariable UUID categoryId,
            @AuthenticationPrincipal SessionUser user) {
        useCase.deleteCategory(restaurantId, user.id(), categoryId);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerMenu.Item createItem(
            @PathVariable UUID restaurantId,
            @RequestBody ItemRequest request,
            @AuthenticationPrincipal SessionUser user) {
        return useCase.createItem(restaurantId, user.id(), request.toUpdate());
    }

    @PutMapping("/items/{itemId}")
    public OwnerMenu.Item updateItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID itemId,
            @RequestBody ItemRequest request,
            @AuthenticationPrincipal SessionUser user) {
        return useCase.updateItem(restaurantId, user.id(), itemId, request.toUpdate());
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal SessionUser user) {
        useCase.deleteItem(restaurantId, user.id(), itemId);
    }
}
