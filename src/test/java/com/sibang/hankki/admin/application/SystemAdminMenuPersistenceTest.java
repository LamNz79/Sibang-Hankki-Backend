package com.sibang.hankki.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.restaurant.application.exception.MenuResourceNotFoundException;
import com.sibang.hankki.restaurant.application.model.OwnerMenuItemUpdate;
import java.math.BigDecimal;
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
class SystemAdminMenuPersistenceTest {

    @Autowired
    private SystemAdminMenuService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void performsCompleteCrudForEveryNonArchivedRestaurantStatus() {
        UUID actorId = insertAdmin();

        for (String status : List.of(
                "DRAFT", "PENDING", "CHANGES_REQUESTED", "ACTIVE", "SUSPENDED")) {
            verifyCompleteCrud(insertRestaurant(status, false), actorId);
        }
    }

    @Test
    void keepsCategoryAndItemOperationsIsolatedByRestaurant() {
        UUID actorId = insertAdmin();
        UUID restaurantA = insertRestaurant("DRAFT", false);
        UUID restaurantB = insertRestaurant("ACTIVE", false);
        var categoryB = service.createCategory(restaurantB, actorId, "B category", 1);
        var itemB = service.createItem(restaurantB, actorId, item(categoryB.id(), "B item"));

        assertThatThrownBy(() -> service.updateCategory(
                restaurantA, actorId, categoryB.id(), "Changed", 2))
                .isInstanceOf(MenuResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deleteCategory(restaurantA, actorId, categoryB.id()))
                .isInstanceOf(MenuResourceNotFoundException.class);
        assertThatThrownBy(() -> service.createItem(
                restaurantA, actorId, item(categoryB.id(), "Cross category")))
                .isInstanceOf(MenuResourceNotFoundException.class);
        assertThatThrownBy(() -> service.updateItem(
                restaurantA, actorId, itemB.id(), item(categoryB.id(), "Changed")))
                .isInstanceOf(MenuResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deleteItem(restaurantA, actorId, itemB.id()))
                .isInstanceOf(MenuResourceNotFoundException.class);

        assertThat(service.get(restaurantA).categories()).isEmpty();
        assertThat(service.get(restaurantA).items()).isEmpty();
        assertThat(service.get(restaurantB).categories()).extracting(category -> category.name())
                .containsExactly("B category");
        assertThat(service.get(restaurantB).items()).extracting(item -> item.name())
                .containsExactly("B item");
    }

    @Test
    void rejectsUnknownAndArchivedRestaurants() {
        UUID actorId = insertAdmin();
        UUID archivedRestaurant = insertRestaurant("SUSPENDED", true);

        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOf(AdminResourceNotFoundException.class);
        assertThatThrownBy(() -> service.createCategory(
                archivedRestaurant, actorId, "Hidden", 1))
                .isInstanceOf(AdminResourceNotFoundException.class);
    }

    private void verifyCompleteCrud(UUID restaurantId, UUID actorId) {
        var category = service.createCategory(restaurantId, actorId, "Main dishes", 1);
        var item = service.createItem(restaurantId, actorId, item(category.id(), "Grilled beef"));
        var updatedCategory = service.updateCategory(
                restaurantId, actorId, category.id(), "Main courses", 2);
        var updatedItem = service.updateItem(
                restaurantId, actorId, item.id(), item(category.id(), "Grilled beef special"));

        assertThat(updatedCategory.name()).isEqualTo("Main courses");
        assertThat(updatedItem.name()).isEqualTo("Grilled beef special");
        assertThat(service.get(restaurantId).categories()).hasSize(1);
        assertThat(service.get(restaurantId).items()).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                "select created_by = ? and updated_by = ? from menus where id = ?",
                Boolean.class, actorId, actorId, item.id())).isTrue();

        service.deleteItem(restaurantId, actorId, item.id());
        service.deleteCategory(restaurantId, actorId, category.id());

        assertThat(service.get(restaurantId).categories()).isEmpty();
        assertThat(service.get(restaurantId).items()).isEmpty();
    }

    private OwnerMenuItemUpdate item(UUID categoryId, String name) {
        return new OwnerMenuItemUpdate(
                categoryId, name, "With vegetables", new BigDecimal("250000"),
                "VND", "https://example.com/image.jpg", true);
    }

    private UUID insertRestaurant(String status, boolean archived) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into restaurants (
                    id, slug, name, city_slug, timezone, approval_status, deleted_at
                ) values (?, ?, ?, 'ho-chi-minh-city', 'Asia/Ho_Chi_Minh', ?,
                    case when ? then current_timestamp else null end)
                """, id, "admin-menu-" + id, "Admin menu " + id, status, archived);
        return id;
    }

    private UUID insertAdmin() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into users (id, userid, password_hash, name, role, status)
                values (?, ?, '$2a$10$not-used-by-this-test', 'Admin', 'ADMIN', 'ACTIVE')
                """, id, "admin-menu-" + id.toString().substring(0, 8));
        return id;
    }
}
