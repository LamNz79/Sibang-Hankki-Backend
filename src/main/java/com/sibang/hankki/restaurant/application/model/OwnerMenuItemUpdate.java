package com.sibang.hankki.restaurant.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record OwnerMenuItemUpdate(
        UUID categoryId,
        String name,
        String description,
        BigDecimal price,
        String currency,
        String imageUrl,
        boolean available) {
}
