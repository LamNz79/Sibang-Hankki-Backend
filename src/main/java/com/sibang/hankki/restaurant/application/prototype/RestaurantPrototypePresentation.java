package com.sibang.hankki.restaurant.application.prototype;

import java.util.Map;

public final class RestaurantPrototypePresentation {

    private static final Map<String, Presentation> BY_SLUG = Map.of(
            "anan-saigon", new Presentation(4.7, 139, "#f6ede4"),
            "royal-pavilion", new Presentation(4.7, 166, "#edf5f4"),
            "refinery", new Presentation(4.7, 121, "#eef4f7"),
            "mori-teppan", new Presentation(4.6, 98, "#edf2f8"),
            "hanoi-hearth", new Presentation(4.6, 87, "#f2ede6"),
            "han-river-dining", new Presentation(4.5, 74, "#e8f3f5"));

    private RestaurantPrototypePresentation() {
    }

    public static Presentation forSlug(String slug) {
        return BY_SLUG.getOrDefault(slug, new Presentation(0, 0, ""));
    }

    public record Presentation(double rating, int ratingCount, String heroAccent) {
    }
}
