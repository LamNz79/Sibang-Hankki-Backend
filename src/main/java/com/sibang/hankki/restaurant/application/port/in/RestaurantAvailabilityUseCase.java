package com.sibang.hankki.restaurant.application.port.in;

import com.sibang.hankki.restaurant.application.model.RestaurantAvailabilityResponse;

public interface RestaurantAvailabilityUseCase {
    RestaurantAvailabilityResponse availability(String slug, String dateValue, int partySize);
}
