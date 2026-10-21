package com.sibang.hankki.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class PublicRestaurantMenuPersistenceTest {

    @Autowired
    private RestaurantCatalogService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void newActiveRestaurantUsesOnlyPersistedCatalogData() {
        TestRestaurant restaurant = insertRestaurant("ACTIVE", false);
        insertTag(restaurant.id(), "special_deal", true);
        insertTag(restaurant.id(), "date_night", false);
        insertHours(restaurant.id());
        insertImage(restaurant.id(), "https://example.com/primary.jpg", "Primary room", 0);
        insertImage(restaurant.id(), "https://example.com/gallery.jpg", "Gallery room", 1);
        UUID category = insertCategory(restaurant.id(), "Main dishes", 0, false);
        insertItem(restaurant.id(), category, "Persisted dish", true, false);

        var summary = service.restaurants(null, null).stream()
                .filter(item -> item.slug().equals(restaurant.slug()))
                .findFirst()
                .orElseThrow();
        var detail = service.restaurant(restaurant.slug()).orElseThrow();

        assertThat(summary.rating()).isZero();
        assertThat(summary.heroAccent()).isEqualTo("#f3f4f6");
        assertThat(summary.imageUrl()).isEqualTo("https://example.com/primary.jpg");
        assertThat(summary.availableText()).isNull();
        assertThat(summary.availableFrom()).isNull();
        assertThat(summary.benefits()).containsExactly("special_deal");
        assertThat(detail.rating()).isZero();
        assertThat(detail.ratingCount()).isZero();
        assertThat(detail.availableText()).isNull();
        assertThat(detail.availableFrom()).isNull();
        assertThat(detail.slotMatrix()).isEmpty();
        assertThat(detail.bookingPolicy()).isNull();
        assertThat(detail.tags()).containsExactlyInAnyOrder("Special deal", "Date night");
        assertThat(detail.openHours()).isEqualTo("11:30 - 22:00");
        assertThat(detail.galleryCount()).isEqualTo(2);
        assertThat(detail.galleryImages()).extracting(image -> image.imageUrl())
                .containsExactly("https://example.com/primary.jpg", "https://example.com/gallery.jpg");
        assertThat(detail.menuCategories()).singleElement()
                .satisfies(menuCategory -> assertThat(menuCategory.items()).singleElement()
                        .satisfies(item -> assertThat(item.name()).isEqualTo("Persisted dish")));
    }

    @Test
    void detailReturnsPersistedPublicBookingPolicy() {
        UUID restaurantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        jdbcTemplate.update("""
                update restaurant_booking_settings
                set booking_window_days = 45,
                    minimum_party_size = 2,
                    maximum_online_party_size = 8,
                    large_party_threshold = 9
                where restaurant_id = ?
                """, restaurantId);

        var policy = service.restaurant("anan-saigon").orElseThrow().bookingPolicy();

        assertThat(policy).isEqualTo(new com.sibang.hankki.restaurant.application.model.RestaurantResponse.BookingPolicy(
                45, 2, 8, 9));
    }

    @Test
    void restaurantListLoadsRelatedDataWithFixedQueryCount() {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        service.restaurants(null, null);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(5);
    }

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

    private void insertTag(UUID restaurantId, String tag, boolean showInBenefits) {
        jdbcTemplate.update("""
                insert into restaurant_tags (id, restaurant_id, tag, show_in_benefits)
                values (?, ?, ?, ?)
                """, UUID.randomUUID(), restaurantId, tag, showInBenefits);
    }

    private void insertHours(UUID restaurantId) {
        jdbcTemplate.update("""
                insert into restaurant_business_hours (id, restaurant_id, day_of_week, opens_at, closes_at)
                values (?, ?, 1, time '11:30', time '22:00')
                """, UUID.randomUUID(), restaurantId);
    }

    private void insertImage(UUID restaurantId, String imageUrl, String altText, int sortOrder) {
        jdbcTemplate.update("""
                insert into restaurant_images (id, restaurant_id, image_url, alt_text, sort_order)
                values (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), restaurantId, imageUrl, altText, sortOrder);
    }

    private record TestRestaurant(UUID id, String slug) {
    }
}
