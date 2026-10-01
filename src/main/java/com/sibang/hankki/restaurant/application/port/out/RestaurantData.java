package com.sibang.hankki.restaurant.application.port.out;

import java.util.UUID;

public record RestaurantData(
        UUID id,
        String slug,
        String name,
        String description,
        String cuisineType,
        String cuisineLabel,
        String citySlug,
        String area,
        String district,
        String address,
        String priceRange) {
}
