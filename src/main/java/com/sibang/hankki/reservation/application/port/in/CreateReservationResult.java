package com.sibang.hankki.reservation.application.port.in;

import com.sibang.hankki.reservation.domain.model.Reservation;
import java.time.LocalDate;
import java.time.LocalTime;

public record CreateReservationResult(
        Reservation reservation,
        String restaurantSlug,
        LocalDate date,
        LocalTime time,
        boolean requiresRestaurantConfirmation,
        boolean idempotentReplay,
        String managementToken) {
}
