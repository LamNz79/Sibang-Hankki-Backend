package com.sibang.hankki.restaurant.adapter.out.persistence;

import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantBusinessHourEntity;
import com.sibang.hankki.restaurant.application.port.out.OwnerBusinessHoursPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantBusinessHourData;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OwnerBusinessHoursPersistenceAdapter implements OwnerBusinessHoursPort {

    private final EntityManager entityManager;

    public OwnerBusinessHoursPersistenceAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<RestaurantBusinessHourData> findByRestaurantId(UUID restaurantId) {
        return entityManager.createQuery("""
                select new com.sibang.hankki.restaurant.application.port.out.RestaurantBusinessHourData(
                    hour.restaurantId, hour.dayOfWeek, hour.opensAt, hour.closesAt)
                from RestaurantBusinessHourEntity hour
                where hour.restaurantId = :restaurantId
                order by hour.dayOfWeek, hour.opensAt
                """, RestaurantBusinessHourData.class)
                .setParameter("restaurantId", restaurantId)
                .getResultList();
    }

    @Override
    public void replace(UUID restaurantId, List<RestaurantBusinessHourData> hours) {
        entityManager.createQuery("""
                delete from RestaurantBusinessHourEntity hour where hour.restaurantId = :restaurantId
                """)
                .setParameter("restaurantId", restaurantId)
                .executeUpdate();
        hours.forEach(hour -> entityManager.persist(new RestaurantBusinessHourEntity(
                restaurantId, hour.dayOfWeek(), hour.opensAt(), hour.closesAt())));
    }
}
