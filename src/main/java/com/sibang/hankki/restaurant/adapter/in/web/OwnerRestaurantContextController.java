package com.sibang.hankki.restaurant.adapter.in.web;

import com.sibang.hankki.auth.SessionUser;
import com.sibang.hankki.restaurant.application.model.OwnerSettings;
import com.sibang.hankki.restaurant.application.port.in.OwnerSettingsUseCase;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owner/restaurant-context")
public class OwnerRestaurantContextController {

    private final OwnerSettingsUseCase settingsUseCase;

    public OwnerRestaurantContextController(OwnerSettingsUseCase settingsUseCase) {
        this.settingsUseCase = settingsUseCase;
    }

    @GetMapping
    public RestaurantContextResponse get(@AuthenticationPrincipal SessionUser user) {
        OwnerSettings settings = settingsUseCase.get(user.restaurantId());
        return new RestaurantContextResponse(
                settings.restaurantId(), settings.slug(), settings.name(), settings.timezone());
    }

    public record RestaurantContextResponse(
            UUID restaurantId,
            String slug,
            String name,
            String timezone) {
    }
}
