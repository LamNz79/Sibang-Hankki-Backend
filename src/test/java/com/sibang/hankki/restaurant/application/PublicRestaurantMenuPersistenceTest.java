package com.sibang.hankki.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class PublicRestaurantMenuPersistenceTest {

    @Autowired
    private RestaurantCatalogService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void detailReadsOnlyAvailableNonDeletedMenuForSelectedRestaurant() {
        TestRestaurant restaurantA = insertRestaurant("ACTIVE", false);
        TestRestaurant restaurantB = insertRestaurant("ACTIVE", false);
        UUID betaCategory = insertCategory(restaurantA.id(), "Beta", 0, false);
        insertCategory(restaurantA.id(), "Empty", 0, false);
        UUID alphaCategory = insertCategory(restaurantA.id(), "Alpha", 0, false);
        UUID deletedCategory = insertCategory(restaurantA.id(), "Deleted", 2, true);
        UUID restaurantBCategory = insertCategory(restaurantB.id(), "Other restaurant", 0, false);

        UUID betaItem = insertItem(restaurantA.id(), betaCategory, "Beta item", true, false);
        UUID alphaItem = insertItem(restaurantA.id(), alphaCategory, "Alpha item", true, false);
        insertItem(restaurantA.id(), alphaCategory, "Unavailable item", false, false);
        insertItem(restaurantA.id(), alphaCategory, "Deleted item", true, true);
        insertItem(restaurantA.id(), deletedCategory, "Hidden category item", true, false);
        insertItem(restaurantB.id(), restaurantBCategory, "Restaurant B item", true, false);

        var response = service.restaurant(restaurantA.slug()).orElseThrow();

        assertThat(response.menuCategories())
                .extracting(category -> category.name())
                .containsExactly("Alpha", "Beta");
        assertThat(response.menuCategories().get(0).items())
                .extracting(item -> item.id())
                .containsExactly(alphaItem);
        assertThat(response.menuCategories().get(1).items())
                .extracting(item -> item.id())
                .containsExactly(betaItem);
        var publicItem = response.menuCategories().get(0).items().get(0);
        assertThat(publicItem.description()).isEqualTo("Alpha item description");
        assertThat(publicItem.price()).isEqualByComparingTo("250000");
        assertThat(publicItem.currency()).isEqualTo("VND");
        assertThat(publicItem.imageUrl()).isEqualTo("https://example.com/item.jpg");
        assertThat(response.menuCategories())
                .flatExtracting(category -> category.items())
                .extracting(item -> item.name())
                .doesNotContain(
                        "Unavailable item", "Deleted item", "Hidden category item", "Restaurant B item");
    }

    @Test
    void inactiveArchivedAndUnknownRestaurantsRemainUnavailable() {
        List<TestRestaurant> hidden = List.of(
                insertRestaurant("DRAFT", false),
                insertRestaurant("PENDING", false),
                insertRestaurant("CHANGES_REQUESTED", false),
                insertRestaurant("SUSPENDED", false),
                insertRestaurant("ACTIVE", true));

        for (TestRestaurant restaurant : hidden) {
            assertThat(service.restaurant(restaurant.slug())).isEmpty();
        }
        assertThat(service.restaurant("unknown-public-menu-restaurant")).isEmpty();
    }

    private TestRestaurant insertRestaurant(String status, boolean archived) {
        UUID id = UUID.randomUUID();
        String slug = "public-menu-" + id;
        jdbcTemplate.update("""
                insert into restaurants (
                    id, slug, name, city_slug, timezone, approval_status, deleted_at
                ) values (?, ?, ?, 'ho-chi-minh-city', 'Asia/Ho_Chi_Minh', ?,
                    case when ? then current_timestamp else null end)
                """, id, slug, "Public menu " + id, status, archived);
        return new TestRestaurant(id, slug);
    }

    private UUID insertCategory(UUID restaurantId, String name, int displayOrder, boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into menu_categories (id, restaurant_id, name, display_order, deleted_at)
                values (?, ?, ?, ?, case when ? then current_timestamp else null end)
                """, id, restaurantId, name, displayOrder, deleted);
        return id;
    }

    private UUID insertItem(
            UUID restaurantId, UUID categoryId, String name, boolean available, boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into menus (
                    id, restaurant_id, category_id, name, description, price, currency, image_url,
                    is_available, deleted_at
                ) values (?, ?, ?, ?, ?, 250000, 'VND', 'https://example.com/item.jpg', ?,
                    case when ? then current_timestamp else null end)
                """, id, restaurantId, categoryId, name, name + " description", available, deleted);
        return id;
    }

    private record TestRestaurant(UUID id, String slug) {
    }
}
