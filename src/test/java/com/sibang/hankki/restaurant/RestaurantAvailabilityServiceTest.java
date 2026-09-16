package com.sibang.hankki.restaurant;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestaurantAvailabilityServiceTest {

    private final RestaurantAvailabilityService service = new RestaurantAvailabilityService();

    @Test
    void mapsGuestCountsOneThroughSixToCapacityBuckets() {
        String date = LocalDate.now().toString();
        RestaurantResponse restaurant = RestaurantData.RESTAURANTS.get(0);

        for (int partySize = 1; partySize <= 6; partySize++) {
            String bucket = partySize <= 2 ? "2" : partySize <= 4 ? "4" : "6";
            RestaurantAvailabilityResponse availability = service.availability("anan-saigon", date, partySize);

            assertEquals("anan-saigon", availability.restaurantSlug());
            assertEquals(partySize, availability.partySize());
            assertEquals(restaurant.slotMatrix().get(date).get(bucket), availability.slots());
        }
    }

    @Test
    void returnsEmptySlotsForSevenToTenGuests() {
        for (int partySize : List.of(7, 10)) {
            RestaurantAvailabilityResponse availability = service.availability("anan-saigon", "2026-09-15", partySize);

            assertEquals(List.of(), availability.slots());
            assertEquals(false, availability.requiresRestaurantConfirmation());
        }
    }

    @Test
    void requiresRestaurantConfirmationForMoreThanTenGuests() {
        RestaurantAvailabilityResponse availability = service.availability("anan-saigon", "2026-09-15", 11);

        assertEquals(11, availability.partySize());
        assertEquals(List.of(), availability.slots());
        assertEquals(true, availability.requiresRestaurantConfirmation());
    }

    @Test
    void rejectsZeroAndNegativePartySizes() {
        for (int partySize : List.of(0, -1)) {
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> service.availability("anan-saigon", "2026-09-15", partySize));

            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        }
    }

    @Test
    void returnsEmptySlotsWhenDateHasNoAvailabilityData() {
        assertEquals(List.of(), service.availability("anan-saigon", "2099-01-01", 2).slots());
    }

    @Test
    void rejectsInvalidDate() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.availability("anan-saigon", "15-09-2026", 2));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void returnsNotFoundForUnknownRestaurant() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.availability("unknown", "2026-09-15", 2));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }
}
