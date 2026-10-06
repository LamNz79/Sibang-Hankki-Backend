package com.sibang.hankki.restaurant.adapter.out.persistence.repository;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.BookingSlot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingSlotRepository extends JpaRepository<BookingSlot, UUID> {

    List<BookingSlot> findByRestaurantIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
            UUID restaurantId,
            Instant startsAt,
            Instant endsAt);

    Optional<BookingSlot> findByRestaurantIdAndStartsAt(UUID restaurantId, Instant startsAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select slot from BookingSlot slot where slot.id = :slotId and slot.restaurantId = :restaurantId")
    Optional<BookingSlot> findByIdAndRestaurantIdForUpdate(
            @Param("slotId") UUID slotId, @Param("restaurantId") UUID restaurantId);

    @Modifying
    @Query("""
            update BookingSlot slot
            set slot.capacityReserved = slot.capacityReserved + :partySize,
                slot.updatedAt = CURRENT_TIMESTAMP
            where slot.id = :slotId
              and slot.capacityTotal - slot.capacityReserved >= :partySize
            """)
    int reserveCapacity(@Param("slotId") UUID slotId, @Param("partySize") int partySize);

    @Modifying
    @Query("""
            update BookingSlot slot
            set slot.capacityReserved = slot.capacityReserved - :partySize,
                slot.updatedAt = CURRENT_TIMESTAMP
            where slot.id = :slotId
              and slot.capacityReserved >= :partySize
            """)
    int releaseCapacity(@Param("slotId") UUID slotId, @Param("partySize") int partySize);

    @Modifying
    @Query(value = """
            delete from booking_slots slot
            where slot.restaurant_id = :restaurantId
              and slot.starts_at >= :startsAt
              and slot.starts_at < :endsAt
              and not exists (
                  select 1 from reservations reservation
                  where reservation.booking_slot_id = slot.id
              )
            """, nativeQuery = true)
    int deleteUnreferencedSlots(
            @Param("restaurantId") UUID restaurantId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt);
}
