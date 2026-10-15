package com.sibang.hankki.restaurant.application.port.in;

import java.time.ZoneId;
import java.util.UUID;

public interface OwnerSlotRegenerationUseCase {
    Result regenerate(UUID restaurantId);

    Result regenerateForAdministration(UUID restaurantId, ZoneId timeZone);

    record Result(int deletedSlots, int generatedSlots) {
    }
}
