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
        String priceRange,
        String timezone,
        Double latitude,
        Double longitude) {

    public RestaurantData(
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
        this(id, slug, name, description, cuisineType, cuisineLabel, citySlug, area, district,
                address, priceRange, "Asia/Ho_Chi_Minh", null, null);
    }

    public RestaurantData(
            UUID id, String slug, String name, String description, String cuisineType, String cuisineLabel,
            String citySlug, String area, String district, String address, String priceRange, String timezone) {
        this(id, slug, name, description, cuisineType, cuisineLabel, citySlug, area, district,
                address, priceRange, timezone, null, null);
    }
}
