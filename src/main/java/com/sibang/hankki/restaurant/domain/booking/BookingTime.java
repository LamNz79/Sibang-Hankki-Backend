package com.sibang.hankki.restaurant.domain.booking;

import java.time.ZoneId;
import java.time.DateTimeException;

public final class BookingTime {

    public static final ZoneId RESTAURANT_TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private BookingTime() {
    }

    public static ZoneId restaurantTimeZone(String value) {
        if (value == null || value.isBlank()) {
            return RESTAURANT_TIME_ZONE;
        }
        try {
            return ZoneId.of(value);
        } catch (DateTimeException exception) {
            throw new BookingRuleViolationException("Invalid restaurant timezone: " + value);
        }
    }
}
