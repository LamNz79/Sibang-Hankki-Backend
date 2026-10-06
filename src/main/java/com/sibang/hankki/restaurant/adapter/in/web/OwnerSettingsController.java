package com.sibang.hankki.restaurant.adapter.in.web;

import com.sibang.hankki.auth.SessionUser;
import com.sibang.hankki.restaurant.application.model.OwnerSettings;
import com.sibang.hankki.restaurant.application.model.OwnerSettingsUpdate;
import com.sibang.hankki.restaurant.application.port.in.OwnerSettingsUseCase;
import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owner/settings")
public class OwnerSettingsController {

    private final OwnerSettingsUseCase useCase;

    public OwnerSettingsController(OwnerSettingsUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public OwnerSettingsResponse get(@AuthenticationPrincipal SessionUser user) {
        return OwnerSettingsResponse.from(useCase.get(user.restaurantId()));
    }

    @PutMapping
    public OwnerSettingsResponse update(
            @RequestBody OwnerSettingsRequest request,
            @AuthenticationPrincipal SessionUser user) {
        return OwnerSettingsResponse.from(useCase.update(user.restaurantId(), request.toUpdate()));
    }

    public record OwnerSettingsRequest(
            String name,
            String description,
            String cuisineLabel,
            String area,
            String district,
            String address,
            String phone,
            String email,
            String priceRange,
            int guestCapacity,
            int bookingIntervalMinutes,
            int diningDurationMinutes,
            ConfirmationMode confirmationMode,
            Integer manualConfirmationMinPartySize,
            int bookingWindowDays,
            int minimumPartySize,
            int maximumOnlinePartySize,
            int largePartyThreshold,
            Integer customerCancellationCutoffMinutes) {

        private OwnerSettingsUpdate toUpdate() {
            return new OwnerSettingsUpdate(
                    name, description, cuisineLabel, area, district, address, phone, email, priceRange,
                    guestCapacity, bookingIntervalMinutes, diningDurationMinutes, confirmationMode,
                    manualConfirmationMinPartySize, bookingWindowDays, minimumPartySize,
                    maximumOnlinePartySize, largePartyThreshold, customerCancellationCutoffMinutes);
        }
    }

    public record OwnerSettingsResponse(
            UUID restaurantId,
            String slug,
            String name,
            String description,
            String cuisineLabel,
            String citySlug,
            String area,
            String district,
            String address,
            String phone,
            String email,
            String timezone,
            String priceRange,
            int guestCapacity,
            int bookingIntervalMinutes,
            int diningDurationMinutes,
            ConfirmationMode confirmationMode,
            Integer manualConfirmationMinPartySize,
            int bookingWindowDays,
            int minimumPartySize,
            int maximumOnlinePartySize,
            int largePartyThreshold,
            Integer customerCancellationCutoffMinutes) {

        private static OwnerSettingsResponse from(OwnerSettings settings) {
            return new OwnerSettingsResponse(
                    settings.restaurantId(), settings.slug(), settings.name(), settings.description(),
                    settings.cuisineLabel(), settings.citySlug(), settings.area(), settings.district(),
                    settings.address(), settings.phone(), settings.email(), settings.timezone(),
                    settings.priceRange(), settings.guestCapacity(), settings.bookingIntervalMinutes(),
                    settings.diningDurationMinutes(), settings.confirmationMode(),
                    settings.manualConfirmationMinPartySize(), settings.bookingWindowDays(),
                    settings.minimumPartySize(), settings.maximumOnlinePartySize(),
                    settings.largePartyThreshold(), settings.customerCancellationCutoffMinutes());
        }
    }
}
