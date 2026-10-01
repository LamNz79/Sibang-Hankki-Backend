package com.sibang.hankki.restaurant.domain.booking;

import java.time.Instant;
import java.util.Objects;

public record BookingSlotCapacity(Instant startsAt, int capacityTotal, int capacityReserved) {

    public BookingSlotCapacity {
        Objects.requireNonNull(startsAt, "startsAt is required");
    }
}
