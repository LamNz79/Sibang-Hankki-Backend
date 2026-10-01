package com.sibang.hankki.restaurant.domain.booking;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

public record BusinessPeriod(DayOfWeek dayOfWeek, LocalTime opensAt, LocalTime closesAt) {

    public BusinessPeriod {
        Objects.requireNonNull(dayOfWeek, "dayOfWeek is required");
        Objects.requireNonNull(opensAt, "opensAt is required");
        Objects.requireNonNull(closesAt, "closesAt is required");
    }
}
