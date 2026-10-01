package com.sibang.hankki.restaurant.application.port.out;

import java.time.LocalTime;
import java.util.UUID;

public record RestaurantBusinessHourData(
        UUID restaurantId, short dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
}
