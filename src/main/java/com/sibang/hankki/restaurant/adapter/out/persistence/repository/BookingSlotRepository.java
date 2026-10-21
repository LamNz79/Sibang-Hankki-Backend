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

    @Query(value = """
            select active.starts_at as "startsAt",
                   active.capacity_total as "capacityTotal",
                   coalesce(sum(slot.capacity_reserved), 0) as "capacityReserved"
            from booking_slots active
            join booking_slots slot
              on slot.restaurant_id = active.restaurant_id
             and slot.starts_at = active.starts_at
            where active.restaurant_id = :restaurantId
              and active.starts_at >= :startsAt
              and active.starts_at < :endsAt
              and active.bookable = true
            group by active.id, active.starts_at, active.capacity_total
            order by active.starts_at
            """, nativeQuery = true)
    List<SlotCapacityProjection> findBookableSlotCapacities(
            @Param("restaurantId") UUID restaurantId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt);

    Optional<BookingSlot> findByRestaurantIdAndStartsAtAndBookableTrue(UUID restaurantId, Instant startsAt);

    @Query(value = """
            select exists (
                select 1
                from booking_slots active
                where active.id = :slotId
                  and active.bookable = true
                  and active.capacity_total > 0
                  and active.capacity_total - (
                      select coalesce(sum(slot.capacity_reserved), 0)
                      from booking_slots slot
                      where slot.restaurant_id = active.restaurant_id
                        and slot.starts_at = active.starts_at
                  ) >= :partySize
            )
            """, nativeQuery = true)
    boolean hasAvailableCapacity(@Param("slotId") UUID slotId, @Param("partySize") int partySize);

    Optional<BookingSlot> findFirstByRestaurantIdAndStartsAtLessThanEqualAndEndsAtGreaterThanAndBookableTrueOrderByStartsAtDesc(
            UUID restaurantId, Instant startsAt, Instant endsAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select slot from BookingSlot slot where slot.id = :slotId and slot.restaurantId = :restaurantId")
    Optional<BookingSlot> findByIdAndRestaurantIdForUpdate(
            @Param("slotId") UUID slotId, @Param("restaurantId") UUID restaurantId);

    @Modifying
    @Query(value = """
            update booking_slots active
            set capacity_reserved = active.capacity_reserved + :partySize,
                updated_at = current_timestamp
            where active.id = :slotId
              and active.bookable = true
              and active.capacity_total
                  - active.capacity_reserved
                  - (
                  select coalesce(sum(slot.capacity_reserved), 0)
                  from booking_slots slot
                  where slot.restaurant_id = active.restaurant_id
                    and slot.starts_at = active.starts_at
                    and slot.id <> active.id
              ) >= :partySize
            """, nativeQuery = true)
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
    @Query("update BookingSlot slot set slot.bookable = :bookable, slot.updatedAt = CURRENT_TIMESTAMP "
            + "where slot.id in :slotIds")
    int setBookable(@Param("slotIds") List<UUID> slotIds, @Param("bookable") boolean bookable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BookingSlot slot set slot.capacityTotal = :capacityTotal, slot.updatedAt = CURRENT_TIMESTAMP "
            + "where slot.id in :slotIds")
    int updateCapacityTotal(@Param("slotIds") List<UUID> slotIds, @Param("capacityTotal") int capacityTotal);

    @Modifying
    @Query(value = """
            delete from booking_slots slot
            where slot.restaurant_id = :restaurantId
              and slot.starts_at >= :startsAt
              and slot.starts_at < :endsAt
              and slot.bookable = false
              and not exists (
                  select 1 from reservations reservation
                  where reservation.booking_slot_id = slot.id
              )
              and not exists (
                  select 1 from reservation_proposals proposal
                  where proposal.proposed_booking_slot_id = slot.id
              )
            """, nativeQuery = true)
    int deleteUnreferencedSlots(
            @Param("restaurantId") UUID restaurantId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt);

    interface SlotCapacityProjection {
        Instant getStartsAt();

        int getCapacityTotal();

        long getCapacityReserved();
    }
}
