package com.sibang.hankki.reservation.application.exception;

public class InvalidReservationRequestException extends IllegalArgumentException {

    public InvalidReservationRequestException(String message) {
        super(message);
    }
}
