package com.sibang.hankki.favorite.adapter.out.persistence;

import com.sibang.hankki.favorite.application.port.out.CustomerFavoritePort;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CustomerFavoritePersistenceAdapter implements CustomerFavoritePort {

    private final JdbcTemplate jdbcTemplate;

    public CustomerFavoritePersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<UUID> findActiveRestaurantIds(UUID userId) {
        return jdbcTemplate.queryForList("""
                select favorite.restaurant_id
                from favorites favorite
                join restaurants restaurant on restaurant.id = favorite.restaurant_id
                where favorite.user_id = ?
                    and restaurant.deleted_at is null
                    and restaurant.approval_status = 'ACTIVE'
                order by favorite.created_at desc, favorite.id desc
                """, UUID.class, userId);
    }

    @Override
    public void save(UUID userId, UUID restaurantId) {
        jdbcTemplate.update("""
                insert into favorites (id, user_id, restaurant_id, created_by, updated_by)
                values (?, ?, ?, ?, ?)
                on conflict (user_id, restaurant_id) do nothing
                """, UUID.randomUUID(), userId, restaurantId, userId, userId);
    }

    @Override
    public void delete(UUID userId, UUID restaurantId) {
        jdbcTemplate.update("delete from favorites where user_id = ? and restaurant_id = ?", userId, restaurantId);
    }
}
