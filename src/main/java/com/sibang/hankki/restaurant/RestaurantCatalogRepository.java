package com.sibang.hankki.restaurant;

import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class RestaurantCatalogRepository {

    private final EntityManager entityManager;

    RestaurantCatalogRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    List<RestaurantEntity> findAllActive() {
        return entityManager.createQuery("""
                select restaurant from RestaurantEntity restaurant
                where restaurant.deletedAt is null
                order by restaurant.id
                """, RestaurantEntity.class).getResultList();
    }

    Optional<RestaurantEntity> findActiveBySlug(String slug) {
        return entityManager.createQuery("""
                select restaurant from RestaurantEntity restaurant
                where restaurant.slug = :slug and restaurant.deletedAt is null
                """, RestaurantEntity.class)
                .setParameter("slug", slug)
                .getResultList()
                .stream()
                .findFirst();
    }

    List<RestaurantTagEntity> findTagsByRestaurantIds(Collection<UUID> restaurantIds) {
        return entityManager.createQuery("""
                select tag from RestaurantTagEntity tag
                where tag.restaurantId in :restaurantIds
                order by tag.restaurantId, tag.id
                """, RestaurantTagEntity.class)
                .setParameter("restaurantIds", restaurantIds)
                .getResultList();
    }

    List<RestaurantBusinessHourEntity> findBusinessHoursByRestaurantIds(Collection<UUID> restaurantIds) {
        return entityManager.createQuery("""
                select businessHour from RestaurantBusinessHourEntity businessHour
                where businessHour.restaurantId in :restaurantIds
                order by businessHour.restaurantId, businessHour.dayOfWeek, businessHour.opensAt
                """, RestaurantBusinessHourEntity.class)
                .setParameter("restaurantIds", restaurantIds)
                .getResultList();
    }

    List<Object[]> countActiveImagesByRestaurantIds(Collection<UUID> restaurantIds) {
        return entityManager.createQuery("""
                select image.restaurantId, count(image)
                from RestaurantImageEntity image
                where image.restaurantId in :restaurantIds and image.deletedAt is null
                group by image.restaurantId
                """, Object[].class)
                .setParameter("restaurantIds", restaurantIds)
                .getResultList();
    }
}
