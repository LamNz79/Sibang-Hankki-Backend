package com.sibang.hankki.restaurant.application.exception;

public class BookingSettingsNotConfiguredException extends RuntimeException {

    public BookingSettingsNotConfiguredException() {
        super("Booking settings not configured");
    }
}
