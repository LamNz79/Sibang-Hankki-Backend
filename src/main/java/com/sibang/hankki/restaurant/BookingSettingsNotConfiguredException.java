package com.sibang.hankki.restaurant;

class BookingSettingsNotConfiguredException extends RuntimeException {

    BookingSettingsNotConfiguredException() {
        super("Booking settings not configured");
    }
}
