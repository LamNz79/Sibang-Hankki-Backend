package com.sibang.hankki.home.application.model;

import java.util.List;

public record HomeContent(Hero heroBanner, List<City> cityImages) {

    public record Hero(
            String titleVi,
            String titleEn,
            String titleKo,
            String descriptionVi,
            String descriptionEn,
            String descriptionKo,
            String imageUrl,
            String imageAltVi,
            String imageAltEn,
            String imageAltKo,
            String targetUrl,
            boolean enabled) {
    }

    public record City(
            int provinceCode,
            String citySlug,
            String imageUrl,
            String imageAlt,
            int sortOrder,
            boolean enabled) {
    }

    public record HeroUpdate(
            String titleVi,
            String titleEn,
            String titleKo,
            String descriptionVi,
            String descriptionEn,
            String descriptionKo,
            String imageUrl,
            String imageAltVi,
            String imageAltEn,
            String imageAltKo,
            String targetUrl,
            Boolean enabled) {
    }

    public record CityUpdate(
            String citySlug,
            String imageUrl,
            String imageAlt,
            Integer sortOrder,
            Boolean enabled) {
    }
}
