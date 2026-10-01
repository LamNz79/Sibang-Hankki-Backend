package com.sibang.hankki.restaurant.adapter.out.persistence;

import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantBusinessHourEntity;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantEntity;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantTagEntity;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantCatalogRepository;
import com.sibang.hankki.restaurant.application.port.out.RestaurantBusinessHourData;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantData;
import com.sibang.hankki.restaurant.application.port.out.RestaurantGalleryCount;
import com.sibang.hankki.restaurant.application.port.out.RestaurantTagData;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RestaurantCatalogPersistenceAdapter implements RestaurantCatalogPort {

    private final RestaurantCatalogRepository repository;
    private final EntityManager entityManager;

    public RestaurantCatalogPersistenceAdapter(RestaurantCatalogRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public List<RestaurantData> findAllActiveRestaurants() {
        return repository.findAllActive().stream().map(this::toRestaurantData).toList();
    }

    @Override
    public Optional<RestaurantData> findActiveRestaurantBySlug(String slug) {
        return repository.findActiveBySlug(slug).map(this::toRestaurantData);
    }

    @Override
    public Optional<RestaurantData> findActiveRestaurantById(UUID restaurantId) {
        return repository.findActiveById(restaurantId).map(this::toRestaurantData);
    }

    @Override
    public List<RestaurantTagData> findTagsByRestaurantIds(Collection<UUID> restaurantIds) {
        return repository.findTagsByRestaurantIds(restaurantIds).stream()
                .map(this::toRestaurantTagData)
                .toList();
    }

    @Override
    public List<RestaurantBusinessHourData> findBusinessHoursByRestaurantIds(Collection<UUID> restaurantIds) {
        return repository.findBusinessHoursByRestaurantIds(restaurantIds).stream()
                .map(this::toRestaurantBusinessHourData)
                .toList();
    }

    @Override
    public List<RestaurantGalleryCount> countActiveImagesByRestaurantIds(Collection<UUID> restaurantIds) {
        return entityManager.createQuery("""
                select new com.sibang.hankki.restaurant.application.port.out.RestaurantGalleryCount(
                    image.restaurantId, count(image))
                from RestaurantImageEntity image
                where image.restaurantId in :restaurantIds and image.deletedAt is null
                group by image.restaurantId
                """, RestaurantGalleryCount.class)
                .setParameter("restaurantIds", restaurantIds)
                .getResultList();
    }

    private RestaurantData toRestaurantData(RestaurantEntity restaurant) {
        return new RestaurantData(
                restaurant.getId(),
                restaurant.getSlug(),
                restaurant.getName(),
                restaurant.getDescription(),
                restaurant.getCuisineType(),
                restaurant.getCuisineLabel(),
                restaurant.getCitySlug(),
                restaurant.getArea(),
                restaurant.getDistrict(),
                restaurant.getAddress(),
                restaurant.getPriceRange());
    }

    private RestaurantTagData toRestaurantTagData(RestaurantTagEntity tag) {
        return new RestaurantTagData(tag.getRestaurantId(), tag.getTag(), tag.isShowInBenefits());
    }

    private RestaurantBusinessHourData toRestaurantBusinessHourData(RestaurantBusinessHourEntity businessHour) {
        return new RestaurantBusinessHourData(
                businessHour.getRestaurantId(),
                businessHour.getDayOfWeek(),
                businessHour.getOpensAt(),
                businessHour.getClosesAt());
    }
}
