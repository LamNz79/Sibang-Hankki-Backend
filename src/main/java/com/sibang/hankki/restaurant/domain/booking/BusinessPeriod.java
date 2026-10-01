package com.sibang.hankki.restaurant.domain.booking;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record BusinessPeriod(DayOfWeek dayOfWeek, LocalTime opensAt, LocalTime closesAt) {

    public BusinessPeriod {
        if (dayOfWeek == null) {
            throw new BookingRuleViolationException("dayOfWeek is required");
        }
        if (opensAt == null) {
            throw new BookingRuleViolationException("opensAt is required");
        }
        if (closesAt == null) {
            throw new BookingRuleViolationException("closesAt is required");
        }
        if (!opensAt.isBefore(closesAt)) {
            throw new BookingRuleViolationException("opensAt must be before closesAt");
        }
    }
}
