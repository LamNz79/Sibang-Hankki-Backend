package com.sibang.hankki.restaurant.adapter.out.persistence;

import com.sibang.hankki.restaurant.application.model.OwnerMenu;
import com.sibang.hankki.restaurant.application.model.OwnerMenuItemUpdate;
import com.sibang.hankki.restaurant.application.port.out.OwnerMenuPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OwnerMenuPersistenceAdapter implements OwnerMenuPort {

    private final JdbcTemplate jdbc;

    public OwnerMenuPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OwnerMenu findAll(UUID restaurantId) {
        List<OwnerMenu.Category> categories = jdbc.query("""
                select id, name, display_order from menu_categories
                where restaurant_id = ? and deleted_at is null
                order by display_order, name, id
                """, this::category, restaurantId);
        List<OwnerMenu.Item> items = jdbc.query("""
                select id, category_id, name, description, price, currency, image_url, is_available
                from menus where restaurant_id = ? and deleted_at is null
                order by name, id
                """, this::item, restaurantId);
        return new OwnerMenu(categories, items);
    }

    @Override
    public OwnerMenu.Category createCategory(UUID restaurantId, UUID actorId, String name, int displayOrder) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into menu_categories
                    (id, restaurant_id, name, display_order, created_by, updated_by)
                values (?, ?, ?, ?, ?, ?)
                """, id, restaurantId, name, displayOrder, actorId, actorId);
        return new OwnerMenu.Category(id, name, displayOrder);
    }

    @Override
    public Optional<OwnerMenu.Category> updateCategory(
            UUID restaurantId, UUID actorId, UUID id, String name, int displayOrder) {
        int changed = jdbc.update("""
                update menu_categories set name = ?, display_order = ?, updated_by = ?, updated_at = current_timestamp
                where id = ? and restaurant_id = ? and deleted_at is null
                """, name, displayOrder, actorId, id, restaurantId);
        return changed == 0 ? Optional.empty() : Optional.of(new OwnerMenu.Category(id, name, displayOrder));
    }

    @Override
    public boolean deleteCategory(UUID restaurantId, UUID actorId, UUID id) {
        return jdbc.update("""
                update menu_categories set deleted_at = current_timestamp, updated_by = ?, updated_at = current_timestamp
                where id = ? and restaurant_id = ? and deleted_at is null
                  and not exists (select 1 from menus where category_id = ? and deleted_at is null)
                """, actorId, id, restaurantId, id) == 1;
    }

    @Override
    public Optional<OwnerMenu.Item> createItem(UUID restaurantId, UUID actorId, OwnerMenuItemUpdate update) {
        if (!categoryBelongsToRestaurant(restaurantId, update.categoryId())) {
            return Optional.empty();
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into menus
                    (id, restaurant_id, category_id, name, description, price, currency, image_url,
                     is_available, created_by, updated_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, restaurantId, update.categoryId(), update.name(), update.description(), update.price(),
                update.currency(), update.imageUrl(), update.available(), actorId, actorId);
        return Optional.of(toItem(id, update));
    }

    @Override
    public Optional<OwnerMenu.Item> updateItem(
            UUID restaurantId, UUID actorId, UUID id, OwnerMenuItemUpdate update) {
        if (!categoryBelongsToRestaurant(restaurantId, update.categoryId())) {
            return Optional.empty();
        }
        int changed = jdbc.update("""
                update menus set category_id = ?, name = ?, description = ?, price = ?, currency = ?,
                    image_url = ?, is_available = ?, updated_by = ?, updated_at = current_timestamp
                where id = ? and restaurant_id = ? and deleted_at is null
                """, update.categoryId(), update.name(), update.description(), update.price(), update.currency(),
                update.imageUrl(), update.available(), actorId, id, restaurantId);
        return changed == 0 ? Optional.empty() : Optional.of(toItem(id, update));
    }

    @Override
    public boolean deleteItem(UUID restaurantId, UUID actorId, UUID id) {
        return jdbc.update("""
                update menus set deleted_at = current_timestamp, updated_by = ?, updated_at = current_timestamp
                where id = ? and restaurant_id = ? and deleted_at is null
                """, actorId, id, restaurantId) == 1;
    }

    private boolean categoryBelongsToRestaurant(UUID restaurantId, UUID categoryId) {
        if (categoryId == null) {
            return true;
        }
        Integer count = jdbc.queryForObject("""
                select count(*) from menu_categories
                where id = ? and restaurant_id = ? and deleted_at is null
                """, Integer.class, categoryId, restaurantId);
        return count != null && count == 1;
    }

    private OwnerMenu.Category category(ResultSet result, int row) throws SQLException {
        return new OwnerMenu.Category(
                result.getObject("id", UUID.class), result.getString("name"), result.getInt("display_order"));
    }

    private OwnerMenu.Item item(ResultSet result, int row) throws SQLException {
        return new OwnerMenu.Item(
                result.getObject("id", UUID.class), result.getObject("category_id", UUID.class),
                result.getString("name"), result.getString("description"), result.getBigDecimal("price"),
                result.getString("currency").trim(), result.getString("image_url"), result.getBoolean("is_available"));
    }

    private OwnerMenu.Item toItem(UUID id, OwnerMenuItemUpdate update) {
        return new OwnerMenu.Item(
                id, update.categoryId(), update.name(), update.description(), update.price(), update.currency(),
                update.imageUrl(), update.available());
    }
}
