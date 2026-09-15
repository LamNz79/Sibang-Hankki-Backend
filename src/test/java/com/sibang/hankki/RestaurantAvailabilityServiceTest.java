package com.sibang.hankki;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestaurantAvailabilityServiceTest {

    private final RestaurantAvailabilityService service = new RestaurantAvailabilityService();

    @Test
    void readsSlotsFromExistingRestaurantMatrix() {
        String date = LocalDate.now().toString();

        RestaurantAvailabilityResponse availability = service.availability("anan-saigon", date, 2);

        assertEquals("anan-saigon", availability.restaurantSlug());
        assertEquals(RestaurantData.RESTAURANTS.get(0).slotMatrix().get(date).get("2"), availability.slots());
    }

    @Test
    void returnsEmptySlotsWhenDateHasNoAvailabilityData() {
        assertEquals(java.util.List.of(), service.availability("anan-saigon", "2099-01-01", 2).slots());
    }

    @Test
    void rejectsUnsupportedPartySize() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.availability("anan-saigon", "2026-09-15", 3));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
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
