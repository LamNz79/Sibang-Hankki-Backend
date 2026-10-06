package com.sibang.hankki.restaurant.adapter.out.persistence;

import com.sibang.hankki.restaurant.application.port.out.RestaurantBusinessHourData;
import com.sibang.hankki.restaurant.application.port.out.RestaurantData;
import com.sibang.hankki.restaurant.application.port.out.RestaurantGalleryCount;
import com.sibang.hankki.restaurant.application.port.out.RestaurantImageData;
import com.sibang.hankki.restaurant.application.port.out.RestaurantTagData;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
@Rollback
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class RestaurantCatalogPersistenceAdapterTest {

    private static final UUID ANAN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private RestaurantCatalogPersistenceAdapter catalogAdapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findsAllActiveRestaurantsAndMapsRestaurantFields() {
        List<RestaurantData> restaurants = catalogAdapter.findAllActiveRestaurants();

        assertEquals(6, restaurants.size());
        assertTrue(restaurants.contains(new RestaurantData(
                ANAN_ID,
                "anan-saigon",
                "Anan Saigon",
                "Modern Vietnamese tasting menus with refined plating and a lively city-dining atmosphere.",
                "vietnamese",
                "Vietnamese contemporary",
                "ho-chi-minh-city",
                "District 1",
                "District 1",
                "District 1",
                "150K - 350K")));
    }

    @Test
    void findsActiveRestaurantsBySlugAndIdAndReturnsEmptyForUnknownValues() {
        RestaurantData expected = new RestaurantData(
                ANAN_ID,
                "anan-saigon",
                "Anan Saigon",
                "Modern Vietnamese tasting menus with refined plating and a lively city-dining atmosphere.",
                "vietnamese",
                "Vietnamese contemporary",
                "ho-chi-minh-city",
                "District 1",
                "District 1",
                "District 1",
                "150K - 350K");

        assertEquals(expected, catalogAdapter.findActiveRestaurantBySlug("anan-saigon").orElseThrow());
        assertEquals(expected, catalogAdapter.findActiveRestaurantById(ANAN_ID).orElseThrow());
        assertTrue(catalogAdapter.findActiveRestaurantBySlug("unknown-restaurant").isEmpty());
        assertTrue(catalogAdapter.findActiveRestaurantById(UUID.randomUUID()).isEmpty());
    }

    @Test
    void mapsTagsIncludingShowInBenefitsAndBusinessHours() {
        assertEquals(List.of(
                new RestaurantTagData(ANAN_ID, "michelin", true),
                new RestaurantTagData(ANAN_ID, "special_deal", true),
                new RestaurantTagData(ANAN_ID, "date_night", false)),
                catalogAdapter.findTagsByRestaurantIds(List.of(ANAN_ID)));

        List<RestaurantBusinessHourData> businessHours =
                catalogAdapter.findBusinessHoursByRestaurantIds(List.of(ANAN_ID));
        assertEquals(7, businessHours.size());
        assertEquals(new RestaurantBusinessHourData(ANAN_ID, (short) 1,
                LocalTime.of(11, 30), LocalTime.of(22, 0)), businessHours.get(0));
        assertEquals(new RestaurantBusinessHourData(ANAN_ID, (short) 7,
                LocalTime.of(11, 30), LocalTime.of(22, 0)), businessHours.get(6));
    }

    @Test
    void executesGalleryCountConstructorProjectionAndExcludesSoftDeletedImages() {
        assertEquals(List.of(new RestaurantGalleryCount(ANAN_ID, 5)),
                catalogAdapter.countActiveImagesByRestaurantIds(List.of(ANAN_ID)));

        jdbcTemplate.update("""
                insert into restaurant_images (id, restaurant_id, image_url, alt_text, sort_order, deleted_at)
                values (?, ?, ?, ?, ?, current_timestamp)
                """, UUID.randomUUID(), ANAN_ID, "https://example.test/deleted-image.jpg", "Deleted image", 99);

        assertEquals(List.of(new RestaurantGalleryCount(ANAN_ID, 5)),
                catalogAdapter.countActiveImagesByRestaurantIds(List.of(ANAN_ID)));
    }

    @Test
    void returnsActiveImagesInDisplayOrder() {
        List<RestaurantImageData> images = catalogAdapter.findImagesByRestaurantIds(List.of(ANAN_ID));

        assertEquals(5, images.size());
        assertEquals(ANAN_ID, images.get(0).restaurantId());
        assertEquals(1, images.get(0).sortOrder());
        assertTrue(images.get(0).imageUrl().startsWith("https://images.unsplash.com/"));
    }

    @Test
    void excludesInactiveDeletedAndUnapprovedRestaurants() {
        TestRestaurant active = insertRestaurant("ACTIVE", false);
        List<TestRestaurant> excludedRestaurants = List.of(
                insertRestaurant("SUSPENDED", false),
                insertRestaurant("PENDING", false),
                insertRestaurant("ACTIVE", true));

        List<RestaurantData> activeRestaurants = catalogAdapter.findAllActiveRestaurants();
        assertTrue(activeRestaurants.stream().anyMatch(restaurant -> restaurant.id().equals(active.id())));
        assertTrue(catalogAdapter.findActiveRestaurantBySlug(active.slug()).isPresent());
        assertTrue(catalogAdapter.findActiveRestaurantById(active.id()).isPresent());

        for (TestRestaurant restaurant : excludedRestaurants) {
            assertFalse(activeRestaurants.stream().anyMatch(candidate -> candidate.id().equals(restaurant.id())));
            assertTrue(catalogAdapter.findActiveRestaurantBySlug(restaurant.slug()).isEmpty());
            assertTrue(catalogAdapter.findActiveRestaurantById(restaurant.id()).isEmpty());
        }
    }

    private TestRestaurant insertRestaurant(String approvalStatus, boolean deleted) {
        UUID id = UUID.randomUUID();
        String slug = "catalog-adapter-test-" + id;
        if (deleted) {
            jdbcTemplate.update("""
                    insert into restaurants (id, slug, name, city_slug, approval_status, deleted_at)
                    values (?, ?, ?, ?, ?, current_timestamp)
                    """, id, slug, "Catalog Adapter Test", "ho-chi-minh-city", approvalStatus);
        } else {
            jdbcTemplate.update("""
                    insert into restaurants (id, slug, name, city_slug, approval_status)
                    values (?, ?, ?, ?, ?)
                    """, id, slug, "Catalog Adapter Test", "ho-chi-minh-city", approvalStatus);
        }
        return new TestRestaurant(id, slug);
    }

    private record TestRestaurant(UUID id, String slug) {
    }
}
