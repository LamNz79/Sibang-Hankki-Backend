package com.sibang.hankki.reservation.application.exception;

public class NoActiveBookingSlotException extends RuntimeException {

    public NoActiveBookingSlotException() {
        super("No current service slot is available");
    }
}
