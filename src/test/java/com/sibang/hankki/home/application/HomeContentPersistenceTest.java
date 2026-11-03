package com.sibang.hankki.home.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sibang.hankki.home.application.exception.HomeContentConflictException;
import com.sibang.hankki.home.application.model.HomeContent;
import com.sibang.hankki.home.application.port.in.HomeContentUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class HomeContentPersistenceTest {

    @Autowired
    private HomeContentUseCase useCase;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void seededContentAndHeroUpdateRoundTrip() {
        var seeded = useCase.getAdmin();
        assertThat(seeded.heroBanner().imageUrl())
                .isEqualTo("/images/sample/restaurants/royal-pavilion-dining-hall.jpg");
        assertThat(seeded.cityImages()).extracting(HomeContent.City::citySlug)
                .containsExactly("ho-chi-minh", "hanoi", "da-nang");

        useCase.updateHero(new HomeContent.HeroUpdate(
                "Tiêu đề mới", "New title", "새 제목",
                "Mô tả", "Description", "설명",
                "/images/sample/new-hero.jpg", "Ảnh", "Image", "이미지",
                "/restaurants/anan-saigon", false));

        var saved = useCase.getAdmin().heroBanner();
        assertThat(saved.titleEn()).isEqualTo("New title");
        assertThat(saved.enabled()).isFalse();
        assertThat(useCase.getPublic("en").heroBanner()).isNull();
    }

    @Test
    void cityUpdateUsesUpsertWithoutDuplicates() {
        HomeContent.CityUpdate first = new HomeContent.CityUpdate(
                "test-city", "/images/sample/cities/first.jpg", "First", 5, true);
        HomeContent.CityUpdate second = new HomeContent.CityUpdate(
                "test-city", "/images/sample/cities/second.jpg", "Second", 2, false);

        useCase.upsertCity(9999, first);
        useCase.upsertCity(9999, second);

        assertThat(jdbc.queryForObject(
                "select count(*) from home_city_images where province_code = 9999", Integer.class))
                .isEqualTo(1);
        assertThat(useCase.getAdmin().cityImages()).filteredOn(city -> city.provinceCode() == 9999)
                .singleElement()
                .satisfies(city -> {
                    assertThat(city.imageUrl()).isEqualTo("/images/sample/cities/second.jpg");
                    assertThat(city.enabled()).isFalse();
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void duplicateCitySlugReturnsConflictWithoutCreatingAnotherCity() {
        HomeContent.CityUpdate duplicate = new HomeContent.CityUpdate(
                "ho-chi-minh", "/images/sample/cities/duplicate.jpg", "Duplicate", 4, true);

        assertThatThrownBy(() -> useCase.upsertCity(9999, duplicate))
                .isInstanceOf(HomeContentConflictException.class)
                .hasMessage("citySlug is already assigned to another province");

        assertThat(jdbc.queryForObject(
                "select count(*) from home_city_images where city_slug = 'ho-chi-minh'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from home_city_images where province_code = 9999", Integer.class))
                .isZero();
    }
}
