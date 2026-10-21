package com.sibang.hankki.restaurant.application.port.out;

import java.time.Instant;
import java.util.UUID;

public record BookingSlotState(UUID id, Instant startsAt, Instant endsAt, boolean bookable) {
}
