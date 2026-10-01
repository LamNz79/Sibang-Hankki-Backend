package com.sibang.hankki.reservation.application.exception;

public class ReservationSlotNotFoundException extends RuntimeException {

    public ReservationSlotNotFoundException() {
        super("Booking slot not found");
    }
}
