package com.sibang.hankki.restaurant.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "booking_slots")
public class BookingSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID restaurantId;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private int capacityTotal;

    /** Confirmed reservations consume guest count here; pending reservations do not. */
    @Column(nullable = false)
    private int capacityReserved;

    @CreationTimestamp
    @Column(nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected BookingSlot() {
    }

    public BookingSlot(
            UUID restaurantId,
            Instant startsAt,
            Instant endsAt,
            int capacityTotal,
            int capacityReserved) {
        this.restaurantId = restaurantId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.capacityTotal = capacityTotal;
        this.capacityReserved = capacityReserved;
    }

    public UUID getRestaurantId() {
        return restaurantId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public int getCapacityTotal() {
        return capacityTotal;
    }

    public int getCapacityReserved() {
        return capacityReserved;
    }
}
