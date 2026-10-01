package com.sibang.hankki.restaurant.application.booking;

import com.sibang.hankki.restaurant.adapter.out.persistence.entity.BookingSlot;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantBookingSettings;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantBusinessHourEntity;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.BookingSlotRepository;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantBookingSettingsRepository;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantCatalogRepository;
import com.sibang.hankki.restaurant.application.BookingDomainMapper;
import com.sibang.hankki.restaurant.application.exception.BookingSettingsNotConfiguredException;
import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;
import com.sibang.hankki.restaurant.domain.booking.BookingPolicy;
import com.sibang.hankki.restaurant.domain.booking.BookingRuleViolationException;
import com.sibang.hankki.restaurant.domain.booking.BookingSlotCandidate;
import com.sibang.hankki.restaurant.domain.booking.BookingSlotGenerator;
import com.sibang.hankki.restaurant.domain.booking.BookingTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
    public static final ZoneId RESTAURANT_TIME_ZONE = BookingTime.RESTAURANT_TIME_ZONE;

    private final RestaurantCatalogRepository restaurantRepository;
    private final RestaurantBookingSettingsRepository settingsRepository;
    private final BookingSlotRepository slotRepository;
    private final Clock clock;
    private final BookingSlotGenerator slotGenerator = new BookingSlotGenerator();

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
        if (restaurantId == null) {
            throw new InvalidBookingRequestException("restaurantId is required");
        }
        LocalDate today = LocalDate.now(clock.withZone(BookingTime.RESTAURANT_TIME_ZONE));
        try {
            BookingPolicy.validateGenerationRequestShape(fromDate, toDate, today);
        } catch (BookingRuleViolationException exception) {
            throw new InvalidBookingRequestException(exception.getMessage(), exception);
        }

        restaurantRepository.findActiveById(restaurantId).orElseThrow(RestaurantNotFoundException::new);
        RestaurantBookingSettings settings = settingsRepository.findById(restaurantId)
                .orElseThrow(BookingSettingsNotConfiguredException::new);
        BookingPolicy policy = BookingDomainMapper.policy(settings);
        try {
            policy.validateGenerationRange(fromDate, toDate, today);
        } catch (BookingRuleViolationException exception) {
            throw new InvalidBookingRequestException(exception.getMessage(), exception);
        }

        Instant rangeStart = fromDate.atStartOfDay(BookingTime.RESTAURANT_TIME_ZONE).toInstant();
        Instant rangeEnd = toDate.plusDays(1).atStartOfDay(BookingTime.RESTAURANT_TIME_ZONE).toInstant();
        Set<Instant> existingStarts = new HashSet<>(slotRepository
                .findByRestaurantIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
                        restaurantId, rangeStart, rangeEnd)
                .stream()
                .map(BookingSlot::getStartsAt)
                .toList());

        List<RestaurantBusinessHourEntity> businessHours = restaurantRepository
                .findBusinessHoursByRestaurantIds(List.of(restaurantId));
        List<BookingSlot> missingSlots = slotGenerator.generate(
                        fromDate, toDate, policy, BookingDomainMapper.businessPeriods(businessHours))
                .stream()
                .filter(slot -> !existingStarts.contains(slot.startsAt()))
                .map(slot -> toPersistenceSlot(restaurantId, policy, slot))
                .toList();

        if (!missingSlots.isEmpty()) {
            slotRepository.saveAll(missingSlots);
        }
        return missingSlots;
    }

    private BookingSlot toPersistenceSlot(UUID restaurantId, BookingPolicy policy, BookingSlotCandidate slot) {
        return new BookingSlot(
                restaurantId,
                slot.startsAt(),
                slot.endsAt(),
                policy.guestCapacity(),
                0);
    }
}
