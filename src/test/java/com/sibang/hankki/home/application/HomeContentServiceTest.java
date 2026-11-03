package com.sibang.hankki.home.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.home.application.exception.InvalidHomeContentException;
import com.sibang.hankki.home.application.model.HomeContent;
import com.sibang.hankki.home.application.port.out.HomeContentPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HomeContentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T01:00:00Z");

    private HomeContentPort port;
    private HomeContentService service;

    @BeforeEach
    void setUp() {
        port = org.mockito.Mockito.mock(HomeContentPort.class);
        service = new HomeContentService(port, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void publicContentExcludesDisabledEntriesAndOrdersCities() {
        given(port.findHero()).willReturn(Optional.of(hero(false)));
        given(port.findCities()).willReturn(List.of(
                city(48, "da-nang", 2, true),
                city(1, "hanoi", 1, false),
                city(79, "ho-chi-minh", 1, true)));

        var result = service.getPublic("en");

        assertThat(result.heroBanner()).isNull();
        assertThat(result.cityImages()).extracting(value -> value.citySlug())
                .containsExactly("ho-chi-minh", "da-nang");
    }

    @Test
    void localizesVietnameseEnglishAndKoreanAndFallsBack() {
        given(port.findHero()).willReturn(Optional.of(hero(true)));
        given(port.findCities()).willReturn(List.of());

        assertThat(service.getPublic("vi").heroBanner().title()).isEqualTo("Tiêu đề");
        assertThat(service.getPublic("en").heroBanner().title()).isEqualTo("Title");
        assertThat(service.getPublic("ko").heroBanner().title()).isEqualTo("제목");
        assertThat(service.getPublic("ja").heroBanner().title()).isEqualTo("Title");
        assertThat(service.getPublic(null).heroBanner().title()).isEqualTo("Title");

        HomeContent.Hero missingRequestedAndEnglish = new HomeContent.Hero(
                "Tiêu đề", null, null, null, null, "설명", "/images/hero.jpg",
                null, null, "대체 텍스트", "/restaurants/example", true);
        given(port.findHero()).willReturn(Optional.of(missingRequestedAndEnglish));

        assertThat(service.getPublic("ko").heroBanner().title()).isEqualTo("Tiêu đề");
        assertThat(service.getPublic("vi").heroBanner().description()).isEqualTo("설명");
    }

    @Test
    void acceptsSafeRootRelativeUrlsAndNormalizesText() {
        HomeContent.Hero expected = new HomeContent.Hero(
                "Tiêu đề", "Title", "제목", null, null, null,
                "/images/sample/hero.jpg", null, null, null, "/restaurants/example", true);
        given(port.saveHero(expected, NOW)).willReturn(expected);

        var result = service.updateHero(new HomeContent.HeroUpdate(
                " Tiêu đề ", " Title ", " 제목 ", null, null, null,
                "/images/sample/hero.jpg", null, null, null, "/restaurants/example", true));

        assertThat(result).isEqualTo(expected);
        verify(port).saveHero(expected, NOW);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)", "file:///secret.jpg", "data:image/png;base64,abc",
            "//example.com/image.jpg", "/images\\secret.jpg"
    })
    void rejectsUnsafeImageUrls(String imageUrl) {
        assertThatThrownBy(() -> service.updateHero(heroUpdate(imageUrl, "/restaurants/example")))
                .isInstanceOf(InvalidHomeContentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/restaurants/example", "//example.com/path",
            "/restaurants\\example", "javascript:alert(1)"
    })
    void rejectsUnsafeTargetUrls(String targetUrl) {
        assertThatThrownBy(() -> service.updateHero(heroUpdate("https://example.com/hero.jpg", targetUrl)))
                .isInstanceOf(InvalidHomeContentException.class);
    }

    @Test
    void validatesCityFieldsAndUpsertsValidCity() {
        HomeContent.City expected = new HomeContent.City(
                79, "ho-chi-minh", "/images/sample/cities/ho-chi-minh-city.jpg", "City", 0, true);
        given(port.saveCity(expected, NOW)).willReturn(expected);

        assertThat(service.upsertCity(79, new HomeContent.CityUpdate(
                "ho-chi-minh", "/images/sample/cities/ho-chi-minh-city.jpg", "City", 0, true)))
                .isEqualTo(expected);
        assertThatThrownBy(() -> service.upsertCity(0, new HomeContent.CityUpdate(
                "city", "/images/city.jpg", null, 0, true)))
                .isInstanceOf(InvalidHomeContentException.class);
        assertThatThrownBy(() -> service.upsertCity(79, new HomeContent.CityUpdate(
                "Ho Chi Minh", "/images/city.jpg", null, 0, true)))
                .isInstanceOf(InvalidHomeContentException.class);
        assertThatThrownBy(() -> service.upsertCity(79, new HomeContent.CityUpdate(
                "city", "/images/city.jpg", null, -1, true)))
                .isInstanceOf(InvalidHomeContentException.class);
    }

    private HomeContent.Hero hero(boolean enabled) {
        return new HomeContent.Hero(
                "Tiêu đề", "Title", "제목",
                "Mô tả", "Description", "설명",
                "/images/hero.jpg", "Ảnh", "Image", "이미지",
                "/restaurants/example", enabled);
    }

    private HomeContent.City city(int provinceCode, String slug, int order, boolean enabled) {
        return new HomeContent.City(
                provinceCode, slug, "/images/cities/" + slug + ".jpg", slug, order, enabled);
    }

    private HomeContent.HeroUpdate heroUpdate(String imageUrl, String targetUrl) {
        return new HomeContent.HeroUpdate(
                "Title", "Title", "Title", null, null, null,
                imageUrl, null, null, null, targetUrl, true);
    }
}
