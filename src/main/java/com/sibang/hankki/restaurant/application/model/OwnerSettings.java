package com.sibang.hankki.restaurant.application.model;

import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;
import java.util.UUID;

public record OwnerSettings(
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
}
