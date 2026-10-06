package com.sibang.hankki.restaurant.application.model;

import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;

public record OwnerSettingsUpdate(
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
}
