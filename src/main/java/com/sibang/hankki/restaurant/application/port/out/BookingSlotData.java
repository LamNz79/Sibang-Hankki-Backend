package com.sibang.hankki.restaurant.application.port.out;

import java.time.Instant;
import java.util.UUID;

public record BookingSlotData(UUID id, UUID restaurantId, Instant startsAt, Instant endsAt) {
}
