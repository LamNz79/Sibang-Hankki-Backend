package com.sibang.hankki.restaurant.adapter.out.persistence;

import com.sibang.hankki.restaurant.application.model.OwnerMedia;
import com.sibang.hankki.restaurant.application.port.out.OwnerMediaPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OwnerMediaPersistenceAdapter implements OwnerMediaPort {

    private final JdbcTemplate jdbc;

    public OwnerMediaPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<OwnerMedia.Image> findAll(UUID restaurantId) {
        return jdbc.query("""
                select id, image_url, alt_text, sort_order from restaurant_images
                where restaurant_id = ? and deleted_at is null
                order by sort_order, id
                """, this::image, restaurantId);
    }

    @Override
    public OwnerMedia.Image create(UUID restaurantId, OwnerMedia.Update update) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into restaurant_images (id, restaurant_id, image_url, alt_text, sort_order)
                values (?, ?, ?, ?, ?)
                """, id, restaurantId, update.imageUrl(), update.altText(), update.sortOrder());
        return toImage(id, update);
    }

    @Override
    public Optional<OwnerMedia.Image> update(UUID restaurantId, UUID id, OwnerMedia.Update update) {
        int changed = jdbc.update("""
                update restaurant_images set image_url = ?, alt_text = ?, sort_order = ?,
                    updated_at = current_timestamp
                where id = ? and restaurant_id = ? and deleted_at is null
                """, update.imageUrl(), update.altText(), update.sortOrder(), id, restaurantId);
        return changed == 0 ? Optional.empty() : Optional.of(toImage(id, update));
    }

    @Override
    public boolean delete(UUID restaurantId, UUID id) {
        return jdbc.update("""
                update restaurant_images set deleted_at = current_timestamp, updated_at = current_timestamp
                where id = ? and restaurant_id = ? and deleted_at is null
                """, id, restaurantId) == 1;
    }

    private OwnerMedia.Image image(ResultSet result, int row) throws SQLException {
        return new OwnerMedia.Image(
                result.getObject("id", UUID.class), result.getString("image_url"),
                result.getString("alt_text"), result.getInt("sort_order"));
    }

    private OwnerMedia.Image toImage(UUID id, OwnerMedia.Update update) {
        return new OwnerMedia.Image(id, update.imageUrl(), update.altText(), update.sortOrder());
    }
}
