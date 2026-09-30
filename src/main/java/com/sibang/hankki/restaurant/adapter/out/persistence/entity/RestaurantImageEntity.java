package com.sibang.hankki.restaurant.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restaurant_images")
class RestaurantImageEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID restaurantId;

    private Instant deletedAt;
}
