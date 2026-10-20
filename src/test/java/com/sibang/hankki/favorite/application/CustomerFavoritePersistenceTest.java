package com.sibang.hankki.favorite.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;
import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import com.sibang.hankki.user.adapter.out.persistence.repository.UserAuthenticationRepository;
import jakarta.persistence.EntityManager;
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
class CustomerFavoritePersistenceTest {

    @Autowired
    private CustomerFavoriteService service;
    @Autowired
    private UserAuthenticationRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @Test
    void savesListsAndDeletesIdempotentlyWithoutDuplicates() {
        UserEntity customer = customer("favorite-idempotent");

        assertThat(service.favorites(customer.getId())).isEmpty();
        service.save(customer.getId(), "anan-saigon");
        service.save(customer.getId(), "anan-saigon");

        assertThat(service.favorites(customer.getId()))
                .extracting(summary -> summary.slug())
                .containsExactly("anan-saigon");
        assertThat(favoriteCount(customer.getId())).isEqualTo(1);

        service.delete(customer.getId(), "anan-saigon");
        service.delete(customer.getId(), "anan-saigon");
        assertThat(service.favorites(customer.getId())).isEmpty();
    }

    @Test
    void favoritesAreIsolatedAndSortedNewestFirst() {
        UserEntity first = customer("favorite-first");
        UserEntity second = customer("favorite-second");
        service.save(first.getId(), "anan-saigon");
        service.save(first.getId(), "hanoi-hearth");
        service.save(second.getId(), "royal-pavilion");
        jdbcTemplate.update("""
                update favorites set created_at = timestamp with time zone '2026-01-01 00:00:00+00'
                where user_id = ? and restaurant_id =
                    (select id from restaurants where slug = 'anan-saigon')
                """, first.getId());
        jdbcTemplate.update("""
                update favorites set created_at = timestamp with time zone '2026-01-02 00:00:00+00'
                where user_id = ? and restaurant_id =
                    (select id from restaurants where slug = 'hanoi-hearth')
                """, first.getId());

        assertThat(service.favorites(first.getId()))
                .extracting(summary -> summary.slug())
                .containsExactly("hanoi-hearth", "anan-saigon");
        assertThat(service.favorites(second.getId()))
                .extracting(summary -> summary.slug())
                .containsExactly("royal-pavilion");
    }

    @Test
    void unavailableRestaurantCannotBeSavedAndExistingFavoriteIsHidden() {
        UserEntity customer = customer("favorite-active-only");
        service.save(customer.getId(), "anan-saigon");
        jdbcTemplate.update("update restaurants set approval_status = 'SUSPENDED' where slug = 'anan-saigon'");
        entityManager.clear();

        assertThat(service.favorites(customer.getId())).isEmpty();
        assertThatThrownBy(() -> service.save(customer.getId(), "anan-saigon"))
                .isInstanceOf(RestaurantNotFoundException.class);
        assertThatThrownBy(() -> service.save(customer.getId(), "unknown-restaurant"))
                .isInstanceOf(RestaurantNotFoundException.class);

        jdbcTemplate.update("update restaurants set deleted_at = current_timestamp where slug = 'royal-pavilion'");
        assertThatThrownBy(() -> service.save(customer.getId(), "royal-pavilion"))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    private UserEntity customer(String userid) {
        return userRepository.saveAndFlush(new UserEntity(
                userid, userid + "@example.com", "hash", "Favorite Customer"));
    }

    private int favoriteCount(UUID userId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from favorites where user_id = ?", Integer.class, userId);
    }
}
