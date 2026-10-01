package com.sibang.hankki.restaurant.domain.booking;

import java.time.Instant;
import java.util.Objects;

public record BookingSlotCandidate(Instant startsAt, Instant endsAt) {

    public BookingSlotCandidate {
        Objects.requireNonNull(startsAt, "startsAt is required");
        Objects.requireNonNull(endsAt, "endsAt is required");
    }
}
