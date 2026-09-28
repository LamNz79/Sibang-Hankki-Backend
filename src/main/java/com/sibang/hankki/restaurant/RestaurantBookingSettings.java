package com.sibang.hankki.restaurant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "restaurant_booking_settings")
public class RestaurantBookingSettings {

    @Id
    private UUID restaurantId;

    @Column(nullable = false)
    private int guestCapacity;

    @Column(nullable = false)
    private short bookingIntervalMinutes;

    @Column(nullable = false)
    private short diningDurationMinutes;

    @Column(nullable = false)
    private short checkoutHoldMinutes;

    @Column(nullable = false)
    private int pendingExpiryMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConfirmationMode confirmationMode;

    private Short manualConfirmationMinPartySize;

    @Column(nullable = false)
    private short bookingWindowDays;

    @Column(nullable = false)
    private short minimumPartySize;

    @Column(nullable = false)
    private short maximumOnlinePartySize;

    @Column(nullable = false)
    private short largePartyThreshold;

    private Integer customerCancellationCutoffMinutes;

    private Integer noShowGraceMinutes;

    @CreationTimestamp
    @Column(nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected RestaurantBookingSettings() {
    }

    RestaurantBookingSettings(
            UUID restaurantId,
            int guestCapacity,
            short bookingIntervalMinutes,
            short diningDurationMinutes,
            short checkoutHoldMinutes,
            int pendingExpiryMinutes,
            ConfirmationMode confirmationMode,
            Short manualConfirmationMinPartySize,
            short bookingWindowDays,
            short minimumPartySize,
            short maximumOnlinePartySize,
            short largePartyThreshold,
            Integer customerCancellationCutoffMinutes,
            Integer noShowGraceMinutes) {
        this.restaurantId = restaurantId;
        this.guestCapacity = guestCapacity;
        this.bookingIntervalMinutes = bookingIntervalMinutes;
        this.diningDurationMinutes = diningDurationMinutes;
        this.checkoutHoldMinutes = checkoutHoldMinutes;
        this.pendingExpiryMinutes = pendingExpiryMinutes;
        this.confirmationMode = confirmationMode;
        this.manualConfirmationMinPartySize = manualConfirmationMinPartySize;
        this.bookingWindowDays = bookingWindowDays;
        this.minimumPartySize = minimumPartySize;
        this.maximumOnlinePartySize = maximumOnlinePartySize;
        this.largePartyThreshold = largePartyThreshold;
        this.customerCancellationCutoffMinutes = customerCancellationCutoffMinutes;
        this.noShowGraceMinutes = noShowGraceMinutes;
    }

    UUID getRestaurantId() {
        return restaurantId;
    }

    ConfirmationMode getConfirmationMode() {
        return confirmationMode;
    }

    Short getManualConfirmationMinPartySize() {
        return manualConfirmationMinPartySize;
    }
}
