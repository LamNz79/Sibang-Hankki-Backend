package com.sibang.hankki.favorite.adapter.in.web;

import com.sibang.hankki.auth.SessionUser;
import com.sibang.hankki.favorite.application.port.in.CustomerFavoriteUseCase;
import com.sibang.hankki.restaurant.application.model.RestaurantSummaryResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer/favorites")
class CustomerFavoriteController {

    private final CustomerFavoriteUseCase useCase;

    CustomerFavoriteController(CustomerFavoriteUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    List<RestaurantSummaryResponse> favorites(@AuthenticationPrincipal SessionUser user) {
        return useCase.favorites(user.id());
    }

    @PutMapping("/{restaurantSlug}")
    ResponseEntity<Void> save(
            @PathVariable String restaurantSlug,
            @AuthenticationPrincipal SessionUser user) {
        useCase.save(user.id(), restaurantSlug);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{restaurantSlug}")
    ResponseEntity<Void> delete(
            @PathVariable String restaurantSlug,
            @AuthenticationPrincipal SessionUser user) {
        useCase.delete(user.id(), restaurantSlug);
        return ResponseEntity.noContent().build();
    }
}
