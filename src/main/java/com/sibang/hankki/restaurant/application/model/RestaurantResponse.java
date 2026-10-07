package com.sibang.hankki.restaurant.application.model;

import java.util.List;
import java.util.Map;

public record RestaurantResponse(
        String slug,
        String name,
        String citySlug,
        String area,
        String district,
        String cuisineLabel,
        String cuisineKey,
        double rating,
        int ratingCount,
        String priceRangeLabel,
        String heroAccent,
        String imageUrl,
        String imageAlt,
        String openHours,
        String address,
        String availableText,
        String availableFrom,
        String priceKey,
        List<String> benefits,
        List<String> tags,
        int galleryCount,
        List<GalleryImage> galleryImages,
        String summary,
        Map<String, Map<String, List<String>>> slotMatrix) {

    public record GalleryImage(String imageUrl, String imageAlt) {
    }
}
