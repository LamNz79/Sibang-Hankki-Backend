package com.sibang.hankki.restaurant.adapter.out.persistence.repository;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.BookingSlot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingSlotRepository extends JpaRepository<BookingSlot, UUID> {

    List<BookingSlot> findByRestaurantIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
            UUID restaurantId,
            Instant startsAt,
            Instant endsAt);

    Optional<BookingSlot> findByRestaurantIdAndStartsAt(UUID restaurantId, Instant startsAt);

    @Modifying
    @Query("""
            update BookingSlot slot
            set slot.capacityReserved = slot.capacityReserved + :partySize,
                slot.updatedAt = CURRENT_TIMESTAMP
            where slot.id = :slotId
              and slot.capacityTotal - slot.capacityReserved >= :partySize
            """)
    int reserveCapacity(@Param("slotId") UUID slotId, @Param("partySize") int partySize);
}
