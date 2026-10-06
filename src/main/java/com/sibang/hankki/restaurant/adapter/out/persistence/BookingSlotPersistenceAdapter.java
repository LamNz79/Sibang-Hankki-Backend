package com.sibang.hankki.restaurant.adapter.out.persistence;

import com.sibang.hankki.restaurant.adapter.out.persistence.entity.BookingSlot;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.BookingSlotRepository;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotPort;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotData;
import com.sibang.hankki.restaurant.domain.booking.BookingSlotCandidate;
import com.sibang.hankki.restaurant.domain.booking.BookingSlotCapacity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class BookingSlotPersistenceAdapter implements BookingSlotPort {

    private final BookingSlotRepository repository;

    public BookingSlotPersistenceAdapter(BookingSlotRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<BookingSlotCapacity> findSlotCapacities(UUID restaurantId, Instant startsAt, Instant endsAt) {
        return repository.findByRestaurantIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
                        restaurantId, startsAt, endsAt)
                .stream()
                .map(slot -> new BookingSlotCapacity(
                        slot.getStartsAt(), slot.getCapacityTotal(), slot.getCapacityReserved()))
                .toList();
    }

    @Override
    public Optional<BookingSlotData> findByRestaurantIdAndStartsAt(UUID restaurantId, Instant startsAt) {
        return repository.findByRestaurantIdAndStartsAt(restaurantId, startsAt)
                .map(slot -> new BookingSlotData(
                        slot.getId(), slot.getRestaurantId(), slot.getStartsAt(), slot.getEndsAt()));
    }

    @Override
    public boolean reserveCapacity(UUID slotId, int partySize) {
        return repository.reserveCapacity(slotId, partySize) == 1;
    }

    @Override
    public boolean lockByIdAndRestaurantId(UUID slotId, UUID restaurantId) {
        return repository.findByIdAndRestaurantIdForUpdate(slotId, restaurantId).isPresent();
    }

    @Override
    public void saveGeneratedSlots(UUID restaurantId, List<BookingSlotCandidate> slots, int capacityTotal) {
        repository.saveAll(slots.stream()
                .map(slot -> new BookingSlot(
                        restaurantId, slot.startsAt(), slot.endsAt(), capacityTotal, 0))
                .toList());
    }
}
