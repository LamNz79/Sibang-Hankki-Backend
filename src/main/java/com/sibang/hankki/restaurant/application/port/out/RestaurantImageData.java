package com.sibang.hankki.restaurant.application.port.out;

import java.util.UUID;

public record RestaurantImageData(
        UUID restaurantId,
        String imageUrl,
        String altText,
        int sortOrder) {
}
