package com.sibang.hankki.home.adapter.out.persistence;

import com.sibang.hankki.home.application.exception.HomeContentConflictException;
import com.sibang.hankki.home.application.model.HomeContent;
import com.sibang.hankki.home.application.port.out.HomeContentPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class HomeContentJdbcAdapter implements HomeContentPort {

    private final JdbcTemplate jdbc;

    public HomeContentJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<HomeContent.Hero> findHero() {
        return jdbc.query("""
                select title_vi, title_en, title_ko,
                    description_vi, description_en, description_ko,
                    image_url, image_alt_vi, image_alt_en, image_alt_ko,
                    target_url, enabled
                from home_hero_banner where id = 1
                """, this::hero).stream().findFirst();
    }

    @Override
    public List<HomeContent.City> findCities() {
        return jdbc.query("""
                select province_code, city_slug, image_url, image_alt, sort_order, enabled
                from home_city_images
                order by sort_order, city_slug
                """, this::city);
    }

    @Override
    public HomeContent.Hero saveHero(HomeContent.Hero hero, Instant now) {
        jdbc.update("""
                insert into home_hero_banner (
                    id, title_vi, title_en, title_ko,
                    description_vi, description_en, description_ko,
                    image_url, image_alt_vi, image_alt_en, image_alt_ko,
                    target_url, enabled, created_at, updated_at
                ) values (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update set
                    title_vi = excluded.title_vi,
                    title_en = excluded.title_en,
                    title_ko = excluded.title_ko,
                    description_vi = excluded.description_vi,
                    description_en = excluded.description_en,
                    description_ko = excluded.description_ko,
                    image_url = excluded.image_url,
                    image_alt_vi = excluded.image_alt_vi,
                    image_alt_en = excluded.image_alt_en,
                    image_alt_ko = excluded.image_alt_ko,
                    target_url = excluded.target_url,
                    enabled = excluded.enabled,
                    updated_at = excluded.updated_at
                """,
                hero.titleVi(), hero.titleEn(), hero.titleKo(),
                hero.descriptionVi(), hero.descriptionEn(), hero.descriptionKo(),
                hero.imageUrl(), hero.imageAltVi(), hero.imageAltEn(), hero.imageAltKo(),
                hero.targetUrl(), hero.enabled(), Timestamp.from(now), Timestamp.from(now));
        return hero;
    }

    @Override
    public HomeContent.City saveCity(HomeContent.City city, Instant now) {
        try {
            jdbc.update("""
                    insert into home_city_images (
                        province_code, city_slug, image_url, image_alt, sort_order, enabled, created_at, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?)
                    on conflict (province_code) do update set
                        city_slug = excluded.city_slug,
                        image_url = excluded.image_url,
                        image_alt = excluded.image_alt,
                        sort_order = excluded.sort_order,
                        enabled = excluded.enabled,
                        updated_at = excluded.updated_at
                    """,
                    city.provinceCode(), city.citySlug(), city.imageUrl(), city.imageAlt(), city.sortOrder(),
                    city.enabled(), Timestamp.from(now), Timestamp.from(now));
        } catch (DataIntegrityViolationException exception) {
            throw new HomeContentConflictException("citySlug is already assigned to another province");
        }
        return city;
    }

    private HomeContent.Hero hero(ResultSet result, int row) throws SQLException {
        return new HomeContent.Hero(
                result.getString("title_vi"), result.getString("title_en"), result.getString("title_ko"),
                result.getString("description_vi"), result.getString("description_en"),
                result.getString("description_ko"), result.getString("image_url"),
                result.getString("image_alt_vi"), result.getString("image_alt_en"),
                result.getString("image_alt_ko"), result.getString("target_url"), result.getBoolean("enabled"));
    }

    private HomeContent.City city(ResultSet result, int row) throws SQLException {
        return new HomeContent.City(
                result.getInt("province_code"), result.getString("city_slug"), result.getString("image_url"),
                result.getString("image_alt"), result.getInt("sort_order"), result.getBoolean("enabled"));
    }
}
