package com.sibang.hankki.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.sibang.hankki.admin.application.SystemAdminRestaurantService;
import com.sibang.hankki.admin.application.port.in.SystemAdminRestaurantUseCase.UpdateRestaurantCommand;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase.Province;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase.Ward;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class RoyalPavilionDemoDataMigrationTest {

    private static final UUID RESTAURANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private SystemAdminRestaurantService adminRestaurantService;

    @Autowired
    private RestaurantCatalogService restaurantCatalogService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private LocationCatalogUseCase locationCatalog;

    @BeforeEach
    void setUpLocationCatalog() {
        org.mockito.BDDMockito.given(locationCatalog.findProvince(79))
                .willReturn(Optional.of(new Province(
                        79, "thanh_pho_ho_chi_minh", "Thành phố Hồ Chí Minh", "thành phố")));
        org.mockito.BDDMockito.given(locationCatalog.findWard(79, 26740))
                .willReturn(Optional.of(new Ward(
                        26740, "phuong_sai_gon", "Phường Sài Gòn", "phường", 79)));
    }

    @Test
    void seededProfileRoundTripsThroughSystemAdminContract() {
        var detail = adminRestaurantService.findById(RESTAURANT_ID);

        assertThat(detail.provinceCode()).isEqualTo(79);
        assertThat(detail.wardCode()).isEqualTo(26740);
        assertThat(detail.wardName()).isEqualTo("Phường Sài Gòn");
        assertThat(detail.address())
                .isEqualTo("22-36 Nguyễn Huệ và 57-69F Đồng Khởi, Phường Sài Gòn, TP.HCM");

        var updated = adminRestaurantService.update(RESTAURANT_ID, new UpdateRestaurantCommand(
                detail.name(), detail.description(), detail.cuisineType(), detail.cuisineLabel(), detail.address(),
                detail.phone(), detail.email(), detail.timezone(), detail.priceRange(),
                detail.provinceCode(), detail.wardCode()));

        assertThat(updated.id()).isEqualTo(RESTAURANT_ID);
        assertThat(updated.provinceCode()).isEqualTo(79);
        assertThat(updated.wardCode()).isEqualTo(26740);
        assertThat(updated.address()).isEqualTo(detail.address());
    }

    @Test
    void publicDetailContainsSeededMenuAndValidSeededMedia() {
        var detail = restaurantCatalogService.restaurant("royal-pavilion").orElseThrow();

        assertThat(detail.menuCategories())
                .extracting(category -> category.name())
                .contains("Royal Pavilion Dim Sum", "Royal Pavilion Signatures");
        assertThat(detail.menuCategories().stream().flatMap(category -> category.items().stream()))
                .hasSizeGreaterThanOrEqualTo(5)
                .allMatch(item -> "VND".equals(item.currency()));
        assertThat(detail.galleryImages())
                .hasSizeGreaterThanOrEqualTo(5)
                .allMatch(image -> image.imageUrl().startsWith("https://")
                        && !image.imageUrl().contains("pinterest.com/pin")
                        && !image.imageUrl().contains("placehold.co")
                        && image.imageAlt() != null
                        && !image.imageAlt().isBlank());

        Integer invalidSeededMedia = jdbcTemplate.queryForObject("""
                select count(*) from restaurant_images
                where restaurant_id = ?
                  and id in (
                    '30000000-0000-0000-0000-000000000021',
                    '30000000-0000-0000-0000-000000000022',
                    '30000000-0000-0000-0000-000000000023',
                    '30000000-0000-0000-0000-000000000024',
                    '30000000-0000-0000-0000-000000000025')
                  and (image_url like '%pinterest.com/pin%' or image_url like 'https://placehold.co/%')
                """, Integer.class, RESTAURANT_ID);
        assertThat(invalidSeededMedia).isZero();
    }

    @Test
    void migrationCanRunAgainWithoutDuplicatesOrChangingOwnerMedia() {
        UUID ownerMediaId = UUID.randomUUID();
        String ownerImageUrl = "https://example.com/owner-image.jpg";
        jdbcTemplate.update("""
                insert into restaurant_images (id, restaurant_id, image_url, alt_text, sort_order)
                values (?, ?, ?, 'Owner image', 99)
                """, ownerMediaId, RESTAURANT_ID, ownerImageUrl);

        executeV18();
        executeV18();

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from menu_categories
                where id in (
                    '40000000-0000-0000-0000-000000000201',
                    '40000000-0000-0000-0000-000000000202')
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from menus
                where id between '41000000-0000-0000-0000-000000000201'::uuid
                    and '41000000-0000-0000-0000-000000000205'::uuid
                """, Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "select image_url from restaurant_images where id = ?", String.class, ownerMediaId))
                .isEqualTo(ownerImageUrl);
    }

    private void executeV18() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                    new ClassPathResource("db/migration/V18__stabilize_royal_pavilion_demo_data.sql")));
            return null;
        });
    }
}
