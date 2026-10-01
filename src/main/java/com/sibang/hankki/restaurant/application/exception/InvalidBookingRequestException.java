package com.sibang.hankki.restaurant.application.exception;

public class InvalidBookingRequestException extends IllegalArgumentException {

    public InvalidBookingRequestException(String message) {
        super(message);
    }

    public InvalidBookingRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
