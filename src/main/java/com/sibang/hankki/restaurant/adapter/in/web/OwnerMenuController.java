package com.sibang.hankki.restaurant.adapter.in.web;

import com.sibang.hankki.auth.SessionUser;
import com.sibang.hankki.restaurant.application.model.OwnerMenu;
import com.sibang.hankki.restaurant.application.model.OwnerMenuItemUpdate;
import com.sibang.hankki.restaurant.application.port.in.OwnerMenuUseCase;
import java.math.BigDecimal;
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
@RequestMapping("/api/owner/menu")
public class OwnerMenuController {

    private final OwnerMenuUseCase useCase;

    public OwnerMenuController(OwnerMenuUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public OwnerMenu get(@AuthenticationPrincipal SessionUser user) {
        return useCase.get(user.restaurantId());
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerMenu.Category createCategory(
            @RequestBody CategoryRequest request, @AuthenticationPrincipal SessionUser user) {
        return useCase.createCategory(user.restaurantId(), user.id(), request.name(), request.displayOrder());
    }

    @PutMapping("/categories/{id}")
    public OwnerMenu.Category updateCategory(
            @PathVariable UUID id,
            @RequestBody CategoryRequest request,
            @AuthenticationPrincipal SessionUser user) {
        return useCase.updateCategory(user.restaurantId(), user.id(), id, request.name(), request.displayOrder());
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable UUID id, @AuthenticationPrincipal SessionUser user) {
        useCase.deleteCategory(user.restaurantId(), user.id(), id);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerMenu.Item createItem(
            @RequestBody ItemRequest request, @AuthenticationPrincipal SessionUser user) {
        return useCase.createItem(user.restaurantId(), user.id(), request.toUpdate());
    }

    @PutMapping("/items/{id}")
    public OwnerMenu.Item updateItem(
            @PathVariable UUID id,
            @RequestBody ItemRequest request,
            @AuthenticationPrincipal SessionUser user) {
        return useCase.updateItem(user.restaurantId(), user.id(), id, request.toUpdate());
    }

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable UUID id, @AuthenticationPrincipal SessionUser user) {
        useCase.deleteItem(user.restaurantId(), user.id(), id);
    }

    public record CategoryRequest(String name, int displayOrder) {
    }

    public record ItemRequest(
            UUID categoryId,
            String name,
            String description,
            BigDecimal price,
            String currency,
            String imageUrl,
            boolean available) {
        private OwnerMenuItemUpdate toUpdate() {
            return new OwnerMenuItemUpdate(
                    categoryId, name, description, price, currency, imageUrl, available);
        }
    }
}
