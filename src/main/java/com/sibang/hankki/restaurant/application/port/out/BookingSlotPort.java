package com.sibang.hankki.restaurant.application.port.out;

import com.sibang.hankki.restaurant.domain.booking.BookingSlotCandidate;
import com.sibang.hankki.restaurant.domain.booking.BookingSlotCapacity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingSlotPort {
    List<BookingSlotCapacity> findSlotCapacities(UUID restaurantId, Instant startsAt, Instant endsAt);

    Optional<BookingSlotData> findByRestaurantIdAndStartsAt(UUID restaurantId, Instant startsAt);

    boolean reserveCapacity(UUID slotId, int partySize);

    void saveGeneratedSlots(UUID restaurantId, List<BookingSlotCandidate> slots, int capacityTotal);
}
