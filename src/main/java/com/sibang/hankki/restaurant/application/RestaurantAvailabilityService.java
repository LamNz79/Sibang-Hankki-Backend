package com.sibang.hankki.restaurant.application;

import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantBookingSettings;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantEntity;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.BookingSlotRepository;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantBookingSettingsRepository;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantCatalogRepository;
import com.sibang.hankki.restaurant.application.exception.BookingSettingsNotConfiguredException;
import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;
import com.sibang.hankki.restaurant.application.model.RestaurantAvailabilityResponse;
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
public class RestaurantAvailabilityService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final RestaurantCatalogRepository restaurantRepository;
    private final RestaurantBookingSettingsRepository settingsRepository;
    private final BookingSlotRepository slotRepository;
    private final Clock clock;

    public RestaurantAvailabilityService(
            RestaurantCatalogRepository restaurantRepository,
            RestaurantBookingSettingsRepository settingsRepository,
            BookingSlotRepository slotRepository,
            Clock clock) {
        this.restaurantRepository = restaurantRepository;
        this.settingsRepository = settingsRepository;
        this.slotRepository = slotRepository;
        this.clock = clock;
    }

    public RestaurantAvailabilityResponse availability(String slug, String dateValue, int partySize) {
        LocalDate date = parseDate(dateValue);
        LocalDate today = LocalDate.now(clock.withZone(BookingTime.RESTAURANT_TIME_ZONE));
        validateBasicRequest(date, today, partySize);

        UUID restaurantId = restaurantRepository.findActiveBySlug(slug)
                .map(RestaurantEntity::getId)
                .orElseThrow(RestaurantNotFoundException::new);
        RestaurantBookingSettings settings = settingsRepository.findById(restaurantId)
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
        List<BookingSlotCapacity> capacities = BookingDomainMapper.slotCapacities(
                slotRepository.findByRestaurantIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
                        restaurantId, start, end));
        return policy.availableSlots(capacities, partySize).stream()
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
