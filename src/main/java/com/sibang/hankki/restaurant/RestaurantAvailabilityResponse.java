package com.sibang.hankki.restaurant;

import java.util.List;

public record RestaurantAvailabilityResponse(
        String restaurantSlug,
        String date,
        int partySize,
        List<String> slots,
        boolean requiresRestaurantConfirmation) {
}
