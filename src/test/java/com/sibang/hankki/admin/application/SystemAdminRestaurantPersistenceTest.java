package com.sibang.hankki.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.exception.AdminRestaurantConflictException;
import com.sibang.hankki.admin.application.port.in.SystemAdminRestaurantUseCase.CreateRestaurantCommand;
import com.sibang.hankki.admin.application.port.in.SystemAdminRestaurantUseCase.UpdateRestaurantCommand;
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
class SystemAdminRestaurantPersistenceTest {

    @Autowired
    private SystemAdminRestaurantService restaurantService;

    @Autowired
    private SystemAdminReadService readService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsUpdatesAndSoftDeletesDraftRestaurant() {
        String slug = uniqueSlug();
        var created = restaurantService.create(createCommand(slug));

        assertThat(created.id()).isNotNull();
        assertThat(created.slug()).isEqualTo(slug);
        assertThat(created.approvalStatus()).isEqualTo("DRAFT");
        assertThat(created.createdAt()).isNotNull();
        assertThat(restaurantService.findById(created.id()).name()).isEqualTo("Restaurant");

        var updated = restaurantService.update(created.id(), new UpdateRestaurantCommand(
                "Updated Restaurant", "Updated description", "Modern", "Modern dining", "da-nang",
                "Hai Chau", "Hai Chau", "2 Main Street", "0911111111", "updated@example.com",
                "Asia/Bangkok", "$$$"));

        assertThat(updated.slug()).isEqualTo(slug);
        assertThat(updated.approvalStatus()).isEqualTo("DRAFT");
        assertThat(updated.name()).isEqualTo("Updated Restaurant");
        assertThat(updated.cuisineType()).isEqualTo("Modern");
        assertThat(updated.timezone()).isEqualTo("Asia/Bangkok");

        restaurantService.archive(created.id());

        assertThatThrownBy(() -> restaurantService.findById(created.id()))
                .isInstanceOf(AdminResourceNotFoundException.class);
        assertThatThrownBy(() -> restaurantService.archive(created.id()))
                .isInstanceOf(AdminResourceNotFoundException.class);
        assertThat(readService.findRestaurants(0, 10, slug, null, null).items()).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "select deleted_at is not null from restaurants where id = ?", Boolean.class, created.id()))
                .isTrue();
    }

    @Test
    void archivedSlugRemainsReserved() {
        String slug = uniqueSlug();
        var created = restaurantService.create(createCommand(slug));
        restaurantService.archive(created.id());

        assertThatThrownBy(() -> restaurantService.create(createCommand(slug)))
                .isInstanceOf(AdminRestaurantConflictException.class);
    }

    private CreateRestaurantCommand createCommand(String slug) {
        return new CreateRestaurantCommand(
                slug, "Restaurant", null, "Vietnamese", "Vietnamese", "ho-chi-minh", null, null, null,
                null, null, null, null);
    }

    private String uniqueSlug() {
        return "admin-crud-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
