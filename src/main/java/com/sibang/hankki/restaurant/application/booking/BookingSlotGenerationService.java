package com.sibang.hankki.restaurant.application.booking;

import com.sibang.hankki.restaurant.application.BookingDomainMapper;
import com.sibang.hankki.restaurant.application.exception.BookingSettingsNotConfiguredException;
import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;
import com.sibang.hankki.restaurant.application.port.out.BookingSettings;
import com.sibang.hankki.restaurant.application.port.out.BookingSettingsPort;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotPort;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotState;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingSlotGenerationService {

    /** Default retained for existing owner and internal generation flows. */
    public static final ZoneId RESTAURANT_TIME_ZONE = BookingTime.RESTAURANT_TIME_ZONE;
    private static final Instant FUTURE_SLOT_LIMIT = Instant.parse("9999-12-31T00:00:00Z");

    private final RestaurantCatalogPort restaurantCatalogPort;
    private final BookingSettingsPort bookingSettingsPort;
    private final BookingSlotPort bookingSlotPort;
    private final Clock clock;
    private final BookingSlotGenerator slotGenerator = new BookingSlotGenerator();

    public BookingSlotGenerationService(
            RestaurantCatalogPort restaurantCatalogPort,
            BookingSettingsPort bookingSettingsPort,
            BookingSlotPort bookingSlotPort,
            Clock clock) {
        this.restaurantCatalogPort = restaurantCatalogPort;
        this.bookingSettingsPort = bookingSettingsPort;
        this.bookingSlotPort = bookingSlotPort;
        this.clock = clock;
    }

    @Transactional
    public List<BookingSlotCandidate> generateSlots(UUID restaurantId, LocalDate fromDate, LocalDate toDate) {
        return synchronize(restaurantId, fromDate, toDate, RESTAURANT_TIME_ZONE, true, false).generatedSlots();
    }

    @Transactional
    public List<BookingSlotCandidate> generateSlotsForAdministration(
            UUID restaurantId, LocalDate fromDate, LocalDate toDate, ZoneId timeZone) {
        return synchronize(restaurantId, fromDate, toDate, timeZone, false, false).generatedSlots();
    }

    @Transactional
    public GenerationResult regenerateSlots(UUID restaurantId, LocalDate fromDate, LocalDate toDate) {
        return synchronize(restaurantId, fromDate, toDate, RESTAURANT_TIME_ZONE, true, true);
    }

    @Transactional
    public GenerationResult regenerateSlotsForAdministration(
            UUID restaurantId, LocalDate fromDate, LocalDate toDate, ZoneId timeZone) {
        return synchronize(restaurantId, fromDate, toDate, timeZone, false, true);
    }

    private GenerationResult synchronize(
            UUID restaurantId,
            LocalDate fromDate,
            LocalDate toDate,
            ZoneId timeZone,
            boolean requireActive,
            boolean removeSlotsAfterWindow) {
        if (restaurantId == null) {
            throw new InvalidBookingRequestException("restaurantId is required");
        }
        if (timeZone == null) {
            throw new InvalidBookingRequestException("timeZone is required");
        }
        LocalDate today = LocalDate.now(clock.withZone(timeZone));
        try {
            BookingPolicy.validateGenerationRequestShape(fromDate, toDate, today);
        } catch (BookingRuleViolationException exception) {
            throw new InvalidBookingRequestException(exception.getMessage(), exception);
        }

        if (requireActive) {
            restaurantCatalogPort.findActiveRestaurantById(restaurantId)
                    .orElseThrow(RestaurantNotFoundException::new);
        }
        BookingSettings settings = bookingSettingsPort.findByRestaurantId(restaurantId)
                .orElseThrow(BookingSettingsNotConfiguredException::new);
        BookingPolicy policy = BookingDomainMapper.policy(settings);
        try {
            policy.validateGenerationRange(fromDate, toDate, today);
        } catch (BookingRuleViolationException exception) {
            throw new InvalidBookingRequestException(exception.getMessage(), exception);
        }

        Instant rangeStart = fromDate.atStartOfDay(timeZone).toInstant();
        Instant rangeEnd = toDate.plusDays(1).atStartOfDay(timeZone).toInstant();
        List<BookingSlotCandidate> desiredSlots = slotGenerator.generate(
                fromDate,
                toDate,
                policy,
                BookingDomainMapper.businessPeriods(
                        restaurantCatalogPort.findBusinessHoursByRestaurantIds(List.of(restaurantId))),
                timeZone);

        Instant cleanupEnd = removeSlotsAfterWindow ? FUTURE_SLOT_LIMIT : rangeEnd;
        List<BookingSlotState> existingSlots = bookingSlotPort.findSlotsForSynchronization(
                restaurantId, rangeStart, cleanupEnd);
        Set<BookingSlotCandidate> desired = new HashSet<>(desiredSlots);
        Map<BookingSlotCandidate, BookingSlotState> retained = new LinkedHashMap<>();
        for (BookingSlotState slot : existingSlots) {
            BookingSlotCandidate key = new BookingSlotCandidate(slot.startsAt(), slot.endsAt());
            if (desired.contains(key)) {
                retained.merge(key, slot, (current, candidate) -> current.bookable() ? current : candidate);
            }
        }

        Set<UUID> retainedIds = retained.values().stream()
                .map(BookingSlotState::id)
                .collect(java.util.stream.Collectors.toSet());
        List<UUID> obsoleteIds = existingSlots.stream()
                .filter(slot -> !retainedIds.contains(slot.id()))
                .filter(BookingSlotState::bookable)
                .map(BookingSlotState::id)
                .toList();
        bookingSlotPort.setBookable(obsoleteIds, false);
        bookingSlotPort.setBookable(retained.values().stream()
                .filter(slot -> !slot.bookable())
                .map(BookingSlotState::id)
                .toList(), true);
        int deleted = bookingSlotPort.deleteUnreferencedSlots(restaurantId, rangeStart, cleanupEnd);

        List<BookingSlotCandidate> missingSlots = desiredSlots.stream()
                .filter(slot -> !retained.containsKey(slot))
                .toList();

        if (!missingSlots.isEmpty()) {
            bookingSlotPort.saveGeneratedSlots(restaurantId, missingSlots, policy.guestCapacity());
        }
        return new GenerationResult(deleted, missingSlots);
    }

    public record GenerationResult(int deletedSlots, List<BookingSlotCandidate> generatedSlots) {
    }
}
