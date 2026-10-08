package com.sibang.hankki.reservation.application.exception;

import java.util.UUID;

public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException() {
        super("Reservation not found");
    }

    public ReservationNotFoundException(String restaurantSlug) {
        super("Restaurant not found: " + restaurantSlug);
    }

    public ReservationNotFoundException(UUID reservationId) {
        super("Reservation not found: " + reservationId);
    }
}
