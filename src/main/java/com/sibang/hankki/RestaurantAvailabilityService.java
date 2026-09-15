package com.sibang.hankki;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class RestaurantAvailabilityService {

    RestaurantAvailabilityResponse availability(String slug, String dateValue, int partySize) {
        LocalDate date = parseDate(dateValue);
        if (partySize <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "partySize must be positive");
        }

        RestaurantResponse restaurant = RestaurantData.RESTAURANTS.stream()
                .filter(candidate -> candidate.slug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found"));
        String capacityBucket = partySize <= 2 ? "2" : partySize <= 4 ? "4" : partySize <= 6 ? "6" : null;
        List<String> slots = capacityBucket == null ? List.of() : restaurant.slotMatrix()
                .getOrDefault(date.toString(), Map.of())
                .getOrDefault(capacityBucket, List.of());

        return new RestaurantAvailabilityResponse(slug, date.toString(), partySize, slots, partySize > 10);
    }

    private LocalDate parseDate(String dateValue) {
        try {
            return LocalDate.parse(dateValue);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date");
        }
    }
}
