package com.sibang.hankki.restaurant.application.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OwnerMenu(List<Category> categories, List<Item> items) {

    public record Category(UUID id, String name, int displayOrder) {
    }

    public record Item(
            UUID id,
            UUID categoryId,
            String name,
            String description,
            BigDecimal price,
            String currency,
            String imageUrl,
            boolean available) {
    }
}
