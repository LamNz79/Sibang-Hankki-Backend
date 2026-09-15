package com.sibang.hankki;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class RestaurantAvailabilityService {

    private static final Set<Integer> SUPPORTED_PARTY_SIZES = Set.of(2, 4, 6);

    RestaurantAvailabilityResponse availability(String slug, String dateValue, int partySize) {
        LocalDate date = parseDate(dateValue);
        if (!SUPPORTED_PARTY_SIZES.contains(partySize)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported partySize");
        }

        RestaurantResponse restaurant = RestaurantData.RESTAURANTS.stream()
                .filter(candidate -> candidate.slug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found"));
        List<String> slots = restaurant.slotMatrix()
                .getOrDefault(date.toString(), Map.of())
                .getOrDefault(Integer.toString(partySize), List.of());

        return new RestaurantAvailabilityResponse(slug, date.toString(), partySize, slots);
    }

    private LocalDate parseDate(String dateValue) {
        try {
            return LocalDate.parse(dateValue);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date");
        }
    }
}
