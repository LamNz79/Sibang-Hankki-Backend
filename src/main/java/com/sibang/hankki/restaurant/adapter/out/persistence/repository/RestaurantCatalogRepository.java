package com.sibang.hankki.restaurant.adapter.out.persistence.repository;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantBusinessHourEntity;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantEntity;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantTagEntity;

import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class RestaurantCatalogRepository {

    private final EntityManager entityManager;

    public RestaurantCatalogRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<RestaurantEntity> findAllActive() {
        return entityManager.createQuery("""
                select restaurant from RestaurantEntity restaurant
                where restaurant.deletedAt is null and restaurant.approvalStatus = 'ACTIVE'
                order by restaurant.id
                """, RestaurantEntity.class).getResultList();
    }

    public Optional<RestaurantEntity> findActiveBySlug(String slug) {
        return entityManager.createQuery("""
                select restaurant from RestaurantEntity restaurant
                where restaurant.slug = :slug
                    and restaurant.deletedAt is null
                    and restaurant.approvalStatus = 'ACTIVE'
                """, RestaurantEntity.class)
                .setParameter("slug", slug)
                .getResultList()
                .stream()
                .findFirst();
    }

    public Optional<RestaurantEntity> findActiveById(UUID id) {
        return entityManager.createQuery("""
                select restaurant from RestaurantEntity restaurant
                where restaurant.id = :id
                    and restaurant.deletedAt is null
                    and restaurant.approvalStatus = 'ACTIVE'
                """, RestaurantEntity.class)
                .setParameter("id", id)
                .getResultList()
                .stream()
                .findFirst();
    }

    public Optional<RestaurantEntity> findById(UUID id) {
        return Optional.ofNullable(entityManager.find(RestaurantEntity.class, id));
    }

    public boolean existsById(UUID id) {
        return entityManager.find(RestaurantEntity.class, id) != null;
    }

    public List<RestaurantTagEntity> findTagsByRestaurantIds(Collection<UUID> restaurantIds) {
        return entityManager.createQuery("""
                select tag from RestaurantTagEntity tag
                where tag.restaurantId in :restaurantIds
                order by tag.restaurantId, tag.id
                """, RestaurantTagEntity.class)
                .setParameter("restaurantIds", restaurantIds)
                .getResultList();
    }

    public List<RestaurantBusinessHourEntity> findBusinessHoursByRestaurantIds(Collection<UUID> restaurantIds) {
        return entityManager.createQuery("""
                select businessHour from RestaurantBusinessHourEntity businessHour
                where businessHour.restaurantId in :restaurantIds
                order by businessHour.restaurantId, businessHour.dayOfWeek, businessHour.opensAt
                """, RestaurantBusinessHourEntity.class)
                .setParameter("restaurantIds", restaurantIds)
                .getResultList();
    }
}
