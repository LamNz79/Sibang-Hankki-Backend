package com.sibang.hankki.home.application.model;

import java.util.List;

public record PublicHomeContent(HeroBanner heroBanner, List<CityImage> cityImages) {

    public record HeroBanner(
            String title,
            String description,
            String imageUrl,
            String imageAlt,
            String targetUrl) {
    }

    public record CityImage(
            int provinceCode,
            String citySlug,
            String imageUrl,
            String imageAlt,
            int sortOrder) {
    }
}
