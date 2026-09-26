package com.sibang.hankki.restaurant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "restaurant_business_hours")
class RestaurantBusinessHourEntity {

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

    UUID getRestaurantId() {
        return restaurantId;
    }

    short getDayOfWeek() {
        return dayOfWeek;
    }

    LocalTime getOpensAt() {
        return opensAt;
    }

    LocalTime getClosesAt() {
        return closesAt;
    }
}
