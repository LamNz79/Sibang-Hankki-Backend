package com.sibang.hankki.restaurant.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "restaurant_business_hours")
public class RestaurantBusinessHourEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID restaurantId;

    @Column(nullable = false)
    private short dayOfWeek;

    @Column(nullable = false)
    private LocalTime opensAt;

    @Column(nullable = false)
    private LocalTime closesAt;

    protected RestaurantBusinessHourEntity() {
    }

    public RestaurantBusinessHourEntity(
            UUID restaurantId, short dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
        this.id = UUID.randomUUID();
        this.restaurantId = restaurantId;
        this.dayOfWeek = dayOfWeek;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
    }

    public UUID getRestaurantId() {
        return restaurantId;
    }

    public short getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getOpensAt() {
        return opensAt;
    }

    public LocalTime getClosesAt() {
        return closesAt;
    }
}
