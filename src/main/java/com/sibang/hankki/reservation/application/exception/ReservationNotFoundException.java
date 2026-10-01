package com.sibang.hankki.reservation.application.exception;

public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(String restaurantSlug) {
        super("Restaurant not found: " + restaurantSlug);
    }
}
