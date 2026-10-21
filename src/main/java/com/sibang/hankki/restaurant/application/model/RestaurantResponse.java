package com.sibang.hankki.restaurant.application.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        Map<String, Map<String, List<String>>> slotMatrix,
        List<MenuCategory> menuCategories,
        BookingPolicy bookingPolicy) {

    public record GalleryImage(String imageUrl, String imageAlt) {
    }

    public record MenuCategory(UUID id, String name, int displayOrder, List<MenuItem> items) {
    }

    public record MenuItem(
            UUID id,
            String name,
            String description,
            BigDecimal price,
            String currency,
            String imageUrl) {
    }

    public record BookingPolicy(
            int bookingWindowDays,
            int minimumPartySize,
            int maximumOnlinePartySize,
            int largePartyThreshold) {
    }
}
