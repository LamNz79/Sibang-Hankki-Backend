package com.sibang.hankki.reservation.application.exception;

public class ReservationBookingSettingsNotConfiguredException extends RuntimeException {

    public ReservationBookingSettingsNotConfiguredException(String restaurantSlug) {
        super("Booking settings are not configured for restaurant: " + restaurantSlug);
    }
}
