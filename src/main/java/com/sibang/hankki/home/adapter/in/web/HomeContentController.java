package com.sibang.hankki.home.adapter.in.web;

import com.sibang.hankki.home.application.model.PublicHomeContent;
import com.sibang.hankki.home.application.port.in.HomeContentUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home-content")
public class HomeContentController {

    private final HomeContentUseCase useCase;

    public HomeContentController(HomeContentUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public HomeContentResponse get(@RequestParam(required = false) String locale) {
        PublicHomeContent content = useCase.getPublic(locale);
        HeroBannerResponse hero = content.heroBanner() == null ? null : new HeroBannerResponse(
                content.heroBanner().title(),
                content.heroBanner().description(),
                content.heroBanner().imageUrl(),
                content.heroBanner().imageAlt(),
                content.heroBanner().targetUrl());
        List<CityImageResponse> cities = content.cityImages().stream()
                .map(city -> new CityImageResponse(
                        city.provinceCode(), city.citySlug(), city.imageUrl(), city.imageAlt(), city.sortOrder()))
                .toList();
        return new HomeContentResponse(hero, cities);
    }

    public record HomeContentResponse(HeroBannerResponse heroBanner, List<CityImageResponse> cityImages) {
    }

    public record HeroBannerResponse(
            String title,
            String description,
            String imageUrl,
            String imageAlt,
            String targetUrl) {
    }

    public record CityImageResponse(
            int provinceCode,
            String citySlug,
            String imageUrl,
            String imageAlt,
            int sortOrder) {
    }
}
