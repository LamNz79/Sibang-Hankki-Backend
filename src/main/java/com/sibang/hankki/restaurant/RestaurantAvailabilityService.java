package com.sibang.hankki.restaurant;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class RestaurantAvailabilityService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final RestaurantCatalogRepository restaurantRepository;
    private final RestaurantBookingSettingsRepository settingsRepository;
    private final BookingSlotRepository slotRepository;
    private final Clock clock;

    RestaurantAvailabilityService(
            RestaurantCatalogRepository restaurantRepository,
            RestaurantBookingSettingsRepository settingsRepository,
            BookingSlotRepository slotRepository,
            Clock clock) {
        this.restaurantRepository = restaurantRepository;
        this.settingsRepository = settingsRepository;
        this.slotRepository = slotRepository;
        this.clock = clock;
    }

    RestaurantAvailabilityResponse availability(String slug, String dateValue, int partySize) {
        LocalDate date = parseDate(dateValue);
        if (partySize <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "partySize must be positive");
        }
        UUID restaurantId = restaurantRepository.findActiveBySlug(slug)
                .map(RestaurantEntity::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found"));
        RestaurantBookingSettings settings = settingsRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Booking settings not configured"));
        LocalDate today = LocalDate.now(clock.withZone(BookingSlotGenerationService.RESTAURANT_TIME_ZONE));
        if (date.isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date must not be in the past");
        }
        if (date.isAfter(today.plusDays(settings.getBookingWindowDays() - 1L))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date exceeds booking window");
        }
        if (partySize < settings.getMinimumPartySize()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "partySize is below the minimum");
        }

        List<String> slots = partySize > settings.getMaximumOnlinePartySize()
                ? List.of()
                : availableSlots(restaurantId, date, partySize);
        return new RestaurantAvailabilityResponse(
                slug,
                date.toString(),
                partySize,
                slots,
                requiresRestaurantConfirmation(settings, partySize));
    }

    private List<String> availableSlots(UUID restaurantId, LocalDate date, int partySize) {
        Instant start = date.atStartOfDay(BookingSlotGenerationService.RESTAURANT_TIME_ZONE).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(BookingSlotGenerationService.RESTAURANT_TIME_ZONE).toInstant();
        return slotRepository.findByRestaurantIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
                        restaurantId, start, end)
                .stream()
                .filter(slot -> slot.getCapacityTotal() > 0
                        && slot.getCapacityTotal() - slot.getCapacityReserved() >= partySize)
                .map(BookingSlot::getStartsAt)
                .map(instant -> TIME.format(instant.atZone(BookingSlotGenerationService.RESTAURANT_TIME_ZONE)))
                .toList();
    }

    private boolean requiresRestaurantConfirmation(RestaurantBookingSettings settings, int partySize) {
        if (partySize >= settings.getLargePartyThreshold()) {
            return true;
        }
        return switch (settings.getConfirmationMode()) {
            case AUTO -> false;
            case MANUAL -> true;
            case HYBRID -> partySize >= settings.getManualConfirmationMinPartySize();
        };
    }

    private LocalDate parseDate(String dateValue) {
        if (dateValue == null || !dateValue.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date");
        }
        try {
            return LocalDate.parse(dateValue, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date");
        }
    }
}
