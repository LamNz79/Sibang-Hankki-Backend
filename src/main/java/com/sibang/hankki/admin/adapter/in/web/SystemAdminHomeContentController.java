package com.sibang.hankki.admin.adapter.in.web;

import com.sibang.hankki.home.application.model.HomeContent;
import com.sibang.hankki.home.application.port.in.HomeContentUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/home-content")
public class SystemAdminHomeContentController {

    private final HomeContentUseCase useCase;

    public SystemAdminHomeContentController(HomeContentUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public HomeContentResponse get() {
        HomeContent content = useCase.getAdmin();
        return new HomeContentResponse(
                content.heroBanner() == null ? null : hero(content.heroBanner()),
                content.cityImages().stream().map(this::city).toList());
    }

    @PutMapping("/hero")
    public HeroResponse updateHero(@RequestBody HeroRequest request) {
        return hero(useCase.updateHero(request.toUpdate()));
    }

    @PutMapping("/cities/{provinceCode}")
    public CityResponse upsertCity(
            @PathVariable int provinceCode,
            @RequestBody CityRequest request) {
        return city(useCase.upsertCity(provinceCode, request.toUpdate()));
    }

    private HeroResponse hero(HomeContent.Hero value) {
        return new HeroResponse(
                value.titleVi(), value.titleEn(), value.titleKo(),
                value.descriptionVi(), value.descriptionEn(), value.descriptionKo(),
                value.imageUrl(), value.imageAltVi(), value.imageAltEn(), value.imageAltKo(),
                value.targetUrl(), value.enabled());
    }

    private CityResponse city(HomeContent.City value) {
        return new CityResponse(
                value.provinceCode(), value.citySlug(), value.imageUrl(), value.imageAlt(),
                value.sortOrder(), value.enabled());
    }

    public record HomeContentResponse(HeroResponse heroBanner, List<CityResponse> cityImages) {
    }

    public record HeroRequest(
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

        HomeContent.HeroUpdate toUpdate() {
            return new HomeContent.HeroUpdate(
                    titleVi, titleEn, titleKo,
                    descriptionVi, descriptionEn, descriptionKo,
                    imageUrl, imageAltVi, imageAltEn, imageAltKo, targetUrl, enabled);
        }
    }

    public record HeroResponse(
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

    public record CityRequest(
            String citySlug,
            String imageUrl,
            String imageAlt,
            Integer sortOrder,
            Boolean enabled) {

        HomeContent.CityUpdate toUpdate() {
            return new HomeContent.CityUpdate(citySlug, imageUrl, imageAlt, sortOrder, enabled);
        }
    }

    public record CityResponse(
            int provinceCode,
            String citySlug,
            String imageUrl,
            String imageAlt,
            int sortOrder,
            boolean enabled) {
    }
}
