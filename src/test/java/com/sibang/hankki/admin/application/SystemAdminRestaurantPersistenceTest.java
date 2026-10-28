package com.sibang.hankki.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.exception.AdminRestaurantConflictException;
import com.sibang.hankki.admin.application.port.in.SystemAdminRestaurantUseCase.CreateRestaurantCommand;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    @MockitoBean
    private LocationCatalogUseCase locationCatalog;

    @BeforeEach
    void setUpLocationCatalog() {
        org.mockito.BDDMockito.given(locationCatalog.findProvince(79))
                .willReturn(Optional.of(new Province(79, "ho_chi_minh", "Thành phố Hồ Chí Minh", "thành phố")));
        org.mockito.BDDMockito.given(locationCatalog.findWard(79, 26734))
                .willReturn(Optional.of(new Ward(26734, "phuong_ben_nghe", "Phường Bến Nghé", "phường", 79)));
    }

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
                "Updated Restaurant", "Updated description", "Modern", "Modern dining", "2 Main Street",
                "0911111111", "updated@example.com", "Asia/Bangkok", "$$$", 79, 26734));

        assertThat(updated.slug()).isEqualTo(slug);
        assertThat(updated.approvalStatus()).isEqualTo("DRAFT");
        assertThat(updated.name()).isEqualTo("Updated Restaurant");
        assertThat(updated.cuisineType()).isEqualTo("Modern");
        assertThat(updated.timezone()).isEqualTo("Asia/Bangkok");
        assertThat(updated.citySlug()).isEqualTo("ho-chi-minh");
        assertThat(updated.wardName()).isEqualTo("Phường Bến Nghé");

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

    @Test
    void rejectsTimezoneChangeAfterBookingSlotsExist() {
        var created = restaurantService.create(createCommand(uniqueSlug()));
        jdbcTemplate.update("""
                insert into booking_slots (
                    id, restaurant_id, starts_at, ends_at, capacity_total, capacity_reserved
                ) values (?, ?, current_timestamp + interval '1 day',
                    current_timestamp + interval '1 day 90 minutes', 20, 0)
                """, UUID.randomUUID(), created.id());

        assertThatThrownBy(() -> restaurantService.update(created.id(), new UpdateRestaurantCommand(
                "Restaurant", null, "Vietnamese", "Vietnamese", null, null, null,
                "Asia/Seoul", null, 79, 26734)))
                .isInstanceOf(AdminRestaurantConflictException.class)
                .hasMessageContaining("timezone cannot be changed");

        assertThat(restaurantService.findById(created.id()).timezone()).isEqualTo("Asia/Ho_Chi_Minh");
    }

    private CreateRestaurantCommand createCommand(String slug) {
        return new CreateRestaurantCommand(
                slug, "Restaurant", null, "Vietnamese", "Vietnamese", null, null, null, null, null, 79, 26734);
    }

    private String uniqueSlug() {
        return "admin-crud-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
