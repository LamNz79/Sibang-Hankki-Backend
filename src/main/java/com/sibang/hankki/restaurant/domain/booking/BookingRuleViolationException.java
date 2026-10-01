package com.sibang.hankki.restaurant.domain.booking;

public class BookingRuleViolationException extends IllegalArgumentException {

    public BookingRuleViolationException(String message) {
        super(message);
    }
}
