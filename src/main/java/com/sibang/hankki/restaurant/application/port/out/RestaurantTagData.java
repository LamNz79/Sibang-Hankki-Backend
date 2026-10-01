package com.sibang.hankki.restaurant.application.port.out;

import java.util.UUID;

public record RestaurantTagData(UUID restaurantId, String tag, boolean showInBenefits) {
}
