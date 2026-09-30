package com.sibang.hankki.restaurant.application.booking;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.BookingSlot;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantBookingSettings;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantBusinessHourEntity;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.BookingSlotRepository;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantBookingSettingsRepository;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantCatalogRepository;
import com.sibang.hankki.restaurant.application.exception.BookingSettingsNotConfiguredException;
import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingSlotGenerationService {

    /** V1 uses one timezone; per-restaurant timezones require a future schema change. */
    public static final ZoneId RESTAURANT_TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final RestaurantCatalogRepository restaurantRepository;
    private final RestaurantBookingSettingsRepository settingsRepository;
    private final BookingSlotRepository slotRepository;
    private final Clock clock;

    BookingSlotGenerationService(
            RestaurantCatalogRepository restaurantRepository,
            RestaurantBookingSettingsRepository settingsRepository,
            BookingSlotRepository slotRepository,
            Clock clock) {
        this.restaurantRepository = restaurantRepository;
        this.settingsRepository = settingsRepository;
        this.slotRepository = slotRepository;
        this.clock = clock;
    }

    @Transactional
    List<BookingSlot> generateSlots(UUID restaurantId, LocalDate fromDate, LocalDate toDate) {
        validateRequiredInputs(restaurantId, fromDate, toDate);
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }
        LocalDate today = LocalDate.now(clock.withZone(RESTAURANT_TIME_ZONE));
        if (fromDate.isBefore(today)) {
            throw new IllegalArgumentException("Cannot generate slots before today");
        }

        restaurantRepository.findActiveById(restaurantId).orElseThrow(RestaurantNotFoundException::new);
        RestaurantBookingSettings settings = settingsRepository.findById(restaurantId)
                .orElseThrow(BookingSettingsNotConfiguredException::new);
        LocalDate latestAllowedDate = today.plusDays(settings.getBookingWindowDays() - 1L);
        if (toDate.isAfter(latestAllowedDate)) {
            throw new IllegalArgumentException("Requested range exceeds booking window");
        }

        Instant rangeStart = fromDate.atStartOfDay(RESTAURANT_TIME_ZONE).toInstant();
        Instant rangeEnd = toDate.plusDays(1).atStartOfDay(RESTAURANT_TIME_ZONE).toInstant();
        Set<Instant> existingStarts = new HashSet<>(slotRepository
                .findByRestaurantIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
                        restaurantId, rangeStart, rangeEnd)
                .stream()
                .map(BookingSlot::getStartsAt)
                .toList());

        List<RestaurantBusinessHourEntity> businessHours = restaurantRepository
                .findBusinessHoursByRestaurantIds(List.of(restaurantId));
        Set<Instant> generatedStarts = new HashSet<>();
        List<BookingSlot> missingSlots = fromDate.datesUntil(toDate.plusDays(1))
                .flatMap(date -> businessHours.stream()
                        .filter(hours -> hours.getDayOfWeek() == date.getDayOfWeek().getValue())
                        .flatMap(hours -> slotsForPeriod(restaurantId, date, hours, settings).stream()))
                .filter(slot -> !existingStarts.contains(slot.getStartsAt()) && generatedStarts.add(slot.getStartsAt()))
                .toList();

        if (!missingSlots.isEmpty()) {
            slotRepository.saveAll(missingSlots);
        }
        return missingSlots;
    }

    private void validateRequiredInputs(UUID restaurantId, LocalDate fromDate, LocalDate toDate) {
        if (restaurantId == null || fromDate == null || toDate == null) {
            throw new IllegalArgumentException("restaurantId, fromDate, and toDate are required");
        }
    }

    private List<BookingSlot> slotsForPeriod(
            UUID restaurantId,
            LocalDate date,
            RestaurantBusinessHourEntity hours,
            RestaurantBookingSettings settings) {
        LocalDateTime closesAt = date.atTime(hours.getClosesAt());
        java.util.ArrayList<BookingSlot> slots = new java.util.ArrayList<>();
        for (LocalDateTime startsAt = date.atTime(hours.getOpensAt());
                !startsAt.plusMinutes(settings.getDiningDurationMinutes()).isAfter(closesAt);
                startsAt = startsAt.plusMinutes(settings.getBookingIntervalMinutes())) {
            slots.add(new BookingSlot(
                    restaurantId,
                    startsAt.atZone(RESTAURANT_TIME_ZONE).toInstant(),
                    startsAt.plusMinutes(settings.getDiningDurationMinutes()).atZone(RESTAURANT_TIME_ZONE).toInstant(),
                    settings.getGuestCapacity(),
                    0));
        }
        return slots;
    }
}
