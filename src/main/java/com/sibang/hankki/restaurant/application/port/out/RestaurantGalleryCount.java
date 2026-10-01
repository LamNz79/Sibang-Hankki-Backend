package com.sibang.hankki.restaurant.application.port.out;

import java.util.UUID;

public record RestaurantGalleryCount(UUID restaurantId, long count) {
}
