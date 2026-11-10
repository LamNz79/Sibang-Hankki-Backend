package com.sibang.hankki.restaurant.application.model;

import java.util.List;

public record RestaurantSummaryResponse(
        String slug,
        String name,
        String citySlug,
        String area,
        String cuisineLabel,
        String cuisineKey,
        double rating,
        String heroAccent,
        String imageUrl,
        String imageAlt,
        String availableText,
        String availableFrom,
        String priceKey,
        List<String> benefits,
        Double latitude,
        Double longitude) {

    public RestaurantSummaryResponse(
            String slug, String name, String citySlug, String area, String cuisineLabel, String cuisineKey,
            double rating, String heroAccent, String imageUrl, String imageAlt, String availableText,
            String availableFrom, String priceKey, List<String> benefits) {
        this(slug, name, citySlug, area, cuisineLabel, cuisineKey, rating, heroAccent, imageUrl, imageAlt,
                availableText, availableFrom, priceKey, benefits, null, null);
    }

    public static RestaurantSummaryResponse from(RestaurantResponse restaurant) {
        return new RestaurantSummaryResponse(
                restaurant.slug(),
                restaurant.name(),
                restaurant.citySlug(),
                restaurant.area(),
                restaurant.cuisineLabel(),
                restaurant.cuisineKey(),
                restaurant.rating(),
                restaurant.heroAccent(),
                restaurant.imageUrl(),
                restaurant.imageAlt(),
                restaurant.availableText(),
                restaurant.availableFrom(),
                restaurant.priceKey(),
                restaurant.benefits(),
                restaurant.latitude(),
                restaurant.longitude());
    }
}
