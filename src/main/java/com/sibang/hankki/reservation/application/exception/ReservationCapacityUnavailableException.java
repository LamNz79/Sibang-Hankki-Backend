package com.sibang.hankki.reservation.application.exception;

public class ReservationCapacityUnavailableException extends RuntimeException {

    public ReservationCapacityUnavailableException() {
        super("Booking slot no longer has sufficient capacity");
    }
}
