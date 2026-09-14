package com.sibang.hankki;

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
        String availableText,
        String availableFrom,
        String priceKey,
        List<String> benefits) {

    static RestaurantSummaryResponse from(RestaurantResponse restaurant) {
        return new RestaurantSummaryResponse(
                restaurant.slug(),
                restaurant.name(),
                restaurant.citySlug(),
                restaurant.area(),
                restaurant.cuisineLabel(),
                restaurant.cuisineKey(),
                restaurant.rating(),
                restaurant.heroAccent(),
                restaurant.availableText(),
                restaurant.availableFrom(),
                restaurant.priceKey(),
                restaurant.benefits());
    }
}
