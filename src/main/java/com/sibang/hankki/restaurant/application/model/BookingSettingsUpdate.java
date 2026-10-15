package com.sibang.hankki.restaurant.application.model;

import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;

public record BookingSettingsUpdate(
        ConfirmationMode confirmationMode,
        Integer manualConfirmationMinPartySize,
        int guestCapacity,
        int bookingIntervalMinutes,
        int diningDurationMinutes,
        int bookingWindowDays,
        int minimumPartySize,
        int maximumOnlinePartySize,
        int largePartyThreshold,
        Integer customerCancellationCutoffMinutes,
        Integer noShowGraceMinutes) {
}
