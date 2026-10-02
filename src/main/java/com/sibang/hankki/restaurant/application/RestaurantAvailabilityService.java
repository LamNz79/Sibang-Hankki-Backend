package com.sibang.hankki.restaurant.application;

import com.sibang.hankki.restaurant.application.exception.BookingSettingsNotConfiguredException;
import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;
import com.sibang.hankki.restaurant.application.model.RestaurantAvailabilityResponse;
import com.sibang.hankki.restaurant.application.port.in.RestaurantAvailabilityUseCase;
import com.sibang.hankki.restaurant.application.port.out.BookingSettings;
import com.sibang.hankki.restaurant.application.port.out.BookingSettingsPort;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
import com.sibang.hankki.restaurant.domain.booking.BookingPolicy;
import com.sibang.hankki.restaurant.domain.booking.BookingRuleViolationException;
import com.sibang.hankki.restaurant.domain.booking.BookingSlotCapacity;
import com.sibang.hankki.restaurant.domain.booking.BookingTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RestaurantAvailabilityService implements RestaurantAvailabilityUseCase {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final RestaurantCatalogPort restaurantCatalogPort;
    private final BookingSettingsPort bookingSettingsPort;
    private final BookingSlotPort bookingSlotPort;
    private final Clock clock;

    public RestaurantAvailabilityService(
            RestaurantCatalogPort restaurantCatalogPort,
            BookingSettingsPort bookingSettingsPort,
            BookingSlotPort bookingSlotPort,
            Clock clock) {
        this.restaurantCatalogPort = restaurantCatalogPort;
        this.bookingSettingsPort = bookingSettingsPort;
        this.bookingSlotPort = bookingSlotPort;
        this.clock = clock;
    }

    @Override
    public RestaurantAvailabilityResponse availability(String slug, String dateValue, int partySize) {
        LocalDate date = parseDate(dateValue);
        LocalDate today = LocalDate.now(clock.withZone(BookingTime.RESTAURANT_TIME_ZONE));
        validateBasicRequest(date, today, partySize);

        UUID restaurantId = restaurantCatalogPort.findActiveRestaurantBySlug(slug)
                .map(restaurant -> restaurant.id())
                .orElseThrow(RestaurantNotFoundException::new);
        BookingSettings settings = bookingSettingsPort.findByRestaurantId(restaurantId)
                .orElseThrow(BookingSettingsNotConfiguredException::new);
        BookingPolicy policy = BookingDomainMapper.policy(settings);
        validateAvailabilityRequest(policy, date, today, partySize);

        List<String> slots = policy.supportsOnlineAvailability(partySize)
                ? availableSlots(restaurantId, date, policy, partySize)
                : List.of();
        return new RestaurantAvailabilityResponse(
                slug,
                date.toString(),
                partySize,
                slots,
                policy.requiresRestaurantConfirmation(partySize));
    }

    private List<String> availableSlots(UUID restaurantId, LocalDate date, BookingPolicy policy, int partySize) {
        Instant start = date.atStartOfDay(BookingTime.RESTAURANT_TIME_ZONE).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(BookingTime.RESTAURANT_TIME_ZONE).toInstant();
        Instant now = Instant.now(clock);
        List<BookingSlotCapacity> capacities = bookingSlotPort.findSlotCapacities(restaurantId, start, end);
        return policy.availableSlots(capacities, partySize).stream()
                .filter(slot -> slot.startsAt().isAfter(now))
                .map(BookingSlotCapacity::startsAt)
                .map(instant -> TIME.format(instant.atZone(BookingTime.RESTAURANT_TIME_ZONE)))
                .toList();
    }

    private void validateBasicRequest(LocalDate date, LocalDate today, int partySize) {
        try {
            BookingPolicy.validateBasicRequest(date, today, partySize);
        } catch (BookingRuleViolationException exception) {
            throw new InvalidBookingRequestException(exception.getMessage(), exception);
        }
    }

    private void validateAvailabilityRequest(BookingPolicy policy, LocalDate date, LocalDate today, int partySize) {
        try {
            policy.validateAvailabilityRequest(date, today, partySize);
        } catch (BookingRuleViolationException exception) {
            throw new InvalidBookingRequestException(exception.getMessage(), exception);
        }
    }

    private LocalDate parseDate(String dateValue) {
        if (dateValue == null || !dateValue.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new InvalidBookingRequestException("Invalid date");
        }
        try {
            return LocalDate.parse(dateValue, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException exception) {
            throw new InvalidBookingRequestException("Invalid date", exception);
        }
    }
}
