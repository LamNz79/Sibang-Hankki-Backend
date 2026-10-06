package com.sibang.hankki.restaurant.application.port.in;

import java.util.UUID;

public interface OwnerSlotRegenerationUseCase {
    Result regenerate(UUID restaurantId);

    record Result(int deletedSlots, int generatedSlots) {
    }
}
