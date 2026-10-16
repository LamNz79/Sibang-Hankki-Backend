package com.sibang.hankki.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.exception.OwnerMediaNotFoundException;
import com.sibang.hankki.restaurant.application.model.OwnerMedia;
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
class SystemAdminMediaPersistenceTest {

    @Autowired
    private SystemAdminMediaService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void performsCompleteCrudForEveryNonArchivedRestaurantStatus() {
        for (String status : List.of(
                "DRAFT", "PENDING", "CHANGES_REQUESTED", "ACTIVE", "SUSPENDED")) {
            verifyCompleteCrud(insertRestaurant(status, false));
        }
    }

    @Test
    void keepsMediaOperationsIsolatedByRestaurant() {
        UUID restaurantA = insertRestaurant("DRAFT", false);
        UUID restaurantB = insertRestaurant("ACTIVE", false);
        OwnerMedia.Image imageB = service.create(restaurantB, update("b.jpg", "Restaurant B", 0));

        assertThatThrownBy(() -> service.update(
                restaurantA, imageB.id(), update("changed.jpg", "Changed", 1)))
                .isInstanceOf(OwnerMediaNotFoundException.class);
        assertThatThrownBy(() -> service.delete(restaurantA, imageB.id()))
                .isInstanceOf(OwnerMediaNotFoundException.class);

        assertThat(service.get(restaurantA).images()).isEmpty();
        assertThat(service.get(restaurantB).images())
                .extracting(OwnerMedia.Image::imageUrl)
                .containsExactly("https://example.com/b.jpg");
    }

    @Test
    void rejectsUnknownArchivedAndInvalidRequests() {
        UUID restaurantId = insertRestaurant("ACTIVE", false);
        UUID archivedRestaurant = insertRestaurant("SUSPENDED", true);

        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOf(AdminResourceNotFoundException.class);
        assertThatThrownBy(() -> service.create(
                archivedRestaurant, update("hidden.jpg", null, 0)))
                .isInstanceOf(AdminResourceNotFoundException.class);
        assertThatThrownBy(() -> service.create(
                restaurantId, new OwnerMedia.Update("file:///secret.jpg", null, 0)))
                .isInstanceOf(InvalidBookingRequestException.class);
        assertThatThrownBy(() -> service.create(
                restaurantId, new OwnerMedia.Update(
                        "https://example.com/image.jpg", "x".repeat(301), 0)))
                .isInstanceOf(InvalidBookingRequestException.class);
        assertThatThrownBy(() -> service.create(
                restaurantId, new OwnerMedia.Update(
                        "https://example.com/image.jpg", null, -1)))
                .isInstanceOf(InvalidBookingRequestException.class);
        assertThat(service.get(restaurantId).images()).isEmpty();
    }

    private void verifyCompleteCrud(UUID restaurantId) {
        OwnerMedia.Image created = service.create(
                restaurantId, update("restaurant.jpg", "Dining room", 0));
        OwnerMedia.Image updated = service.update(
                restaurantId, created.id(), update("updated.jpg", "Main dining room", 2));

        assertThat(updated.imageUrl()).isEqualTo("https://example.com/updated.jpg");
        assertThat(updated.altText()).isEqualTo("Main dining room");
        assertThat(updated.sortOrder()).isEqualTo(2);
        assertThat(service.get(restaurantId).images()).containsExactly(updated);

        service.delete(restaurantId, created.id());
        assertThat(service.get(restaurantId).images()).isEmpty();
    }

    private OwnerMedia.Update update(String fileName, String altText, int sortOrder) {
        return new OwnerMedia.Update("https://example.com/" + fileName, altText, sortOrder);
    }

    private UUID insertRestaurant(String status, boolean archived) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into restaurants (
                    id, slug, name, city_slug, timezone, approval_status, deleted_at
                ) values (?, ?, ?, 'ho-chi-minh-city', 'Asia/Ho_Chi_Minh', ?,
                    case when ? then current_timestamp else null end)
                """, id, "admin-media-" + id, "Admin media " + id, status, archived);
        return id;
    }
}
