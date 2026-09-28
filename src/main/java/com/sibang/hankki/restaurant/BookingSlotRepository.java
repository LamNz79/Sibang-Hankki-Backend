package com.sibang.hankki.restaurant;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BookingSlotRepository extends JpaRepository<BookingSlot, UUID> {

    List<BookingSlot> findByRestaurantIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
            UUID restaurantId,
            Instant startsAt,
            Instant endsAt);
}
