package com.sibang.hankki.restaurant.application.booking;

import com.sibang.hankki.restaurant.application.BookingDomainMapper;
import com.sibang.hankki.restaurant.application.exception.BookingSettingsNotConfiguredException;
import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;
import com.sibang.hankki.restaurant.application.port.out.BookingSettings;
import com.sibang.hankki.restaurant.application.port.out.BookingSettingsPort;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotPort;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingSlotGenerationService {

    /** Default retained for existing owner and internal generation flows. */
    public static final ZoneId RESTAURANT_TIME_ZONE = BookingTime.RESTAURANT_TIME_ZONE;

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
        return generateSlots(restaurantId, fromDate, toDate, RESTAURANT_TIME_ZONE, true);
    }

    @Transactional
    public List<BookingSlotCandidate> generateSlotsForAdministration(
            UUID restaurantId, LocalDate fromDate, LocalDate toDate, ZoneId timeZone) {
        return generateSlots(restaurantId, fromDate, toDate, timeZone, false);
    }

    private List<BookingSlotCandidate> generateSlots(
            UUID restaurantId, LocalDate fromDate, LocalDate toDate, ZoneId timeZone, boolean requireActive) {
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
        Set<Instant> existingStarts = new HashSet<>(bookingSlotPort
                .findSlotCapacities(restaurantId, rangeStart, rangeEnd)
                .stream()
                .map(slot -> slot.startsAt())
                .toList());

        List<BookingSlotCandidate> missingSlots = slotGenerator.generate(
                fromDate,
                toDate,
                policy,
                BookingDomainMapper.businessPeriods(
                        restaurantCatalogPort.findBusinessHoursByRestaurantIds(List.of(restaurantId))),
                timeZone)
                .stream()
                .filter(slot -> !existingStarts.contains(slot.startsAt()))
                .toList();

        if (!missingSlots.isEmpty()) {
            bookingSlotPort.saveGeneratedSlots(restaurantId, missingSlots, policy.guestCapacity());
        }
        return missingSlots;
    }
}
