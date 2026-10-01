package com.sibang.hankki.reservation.application.exception;

public class ReservationIdempotencyConflictException extends RuntimeException {

    public ReservationIdempotencyConflictException() {
        super("Idempotency-Key was already used with a different request");
    }
}
