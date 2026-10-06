package com.sibang.hankki.restaurant.application.port.out;

import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;
import java.util.UUID;

public record BookingSettings(
        UUID restaurantId,
        int guestCapacity,
        int bookingIntervalMinutes,
        int diningDurationMinutes,
        ConfirmationMode confirmationMode,
        Integer manualConfirmationMinPartySize,
        int bookingWindowDays,
        int minimumPartySize,
        int maximumOnlinePartySize,
        int largePartyThreshold,
        Integer customerCancellationCutoffMinutes) {

    public BookingSettings(
            UUID restaurantId,
            int guestCapacity,
            int bookingIntervalMinutes,
            int diningDurationMinutes,
            ConfirmationMode confirmationMode,
            Integer manualConfirmationMinPartySize,
            int bookingWindowDays,
            int minimumPartySize,
            int maximumOnlinePartySize,
            int largePartyThreshold) {
        this(restaurantId, guestCapacity, bookingIntervalMinutes, diningDurationMinutes, confirmationMode,
                manualConfirmationMinPartySize, bookingWindowDays, minimumPartySize,
                maximumOnlinePartySize, largePartyThreshold, null);
    }
}
