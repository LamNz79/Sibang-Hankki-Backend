package com.sibang.hankki.restaurant.domain.booking;

import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    @Test
    void acceptsLastBookableDateAndRejectsPastOrOneDayBeyondWindow() {
        BookingPolicy policy = policy(ConfirmationMode.AUTO, null, 1, 6, 7, 30);

        assertDoesNotThrow(() -> policy.validateAvailabilityRequest(TODAY.plusDays(29), TODAY, 2));
        assertThrows(BookingRuleViolationException.class,
                () -> policy.validateAvailabilityRequest(TODAY.minusDays(1), TODAY, 2));
        assertThrows(BookingRuleViolationException.class,
                () -> policy.validateAvailabilityRequest(TODAY.plusDays(30), TODAY, 2));
    }

    @Test
    void rejectsNonPositiveAndBelowMinimumPartySizes() {
        BookingPolicy policy = policy(ConfirmationMode.AUTO, null, 2, 6, 7, 30);

        assertThrows(BookingRuleViolationException.class,
                () -> policy.validateAvailabilityRequest(TODAY, TODAY, 0));
        assertThrows(BookingRuleViolationException.class,
                () -> policy.validateAvailabilityRequest(TODAY, TODAY, 1));
    }

    @Test
    void hidesOnlineSlotsAboveMaximumWhileKeepingConfirmationRuleIndependent() {
        BookingPolicy policy = policy(ConfirmationMode.AUTO, null, 1, 4, 7, 30);

        assertFalse(policy.supportsOnlineAvailability(5));
        assertFalse(policy.requiresRestaurantConfirmation(5));
        assertTrue(policy.requiresRestaurantConfirmation(7));
    }

    @Test
    void appliesAutoManualHybridAndLargePartyConfirmationRules() {
        BookingPolicy auto = policy(ConfirmationMode.AUTO, null, 1, 10, 11, 30);
        BookingPolicy manual = policy(ConfirmationMode.MANUAL, null, 1, 10, 11, 30);
        BookingPolicy hybrid = policy(ConfirmationMode.HYBRID, 4, 1, 10, 11, 30);

        assertFalse(auto.requiresRestaurantConfirmation(3));
        assertTrue(manual.requiresRestaurantConfirmation(3));
        assertFalse(hybrid.requiresRestaurantConfirmation(3));
        assertTrue(hybrid.requiresRestaurantConfirmation(4));
        assertTrue(auto.requiresRestaurantConfirmation(11));
    }

    @Test
    void returnsOnlySlotsWithEnoughRemainingCapacity() {
        BookingPolicy policy = policy(ConfirmationMode.AUTO, null, 1, 10, 11, 30);
        List<BookingSlotCapacity> slots = List.of(
                capacity("2026-09-28T03:00:00Z", 2, 0),
                capacity("2026-09-28T03:30:00Z", 4, 3),
                capacity("2026-09-28T04:00:00Z", 4, 4),
                capacity("2026-09-28T04:30:00Z", 0, 0));

        assertEquals(List.of(slots.get(0)), policy.availableSlots(slots, 2));
    }

    @Test
    void validatesGenerationRangeInTheBookingWindow() {
        BookingPolicy policy = policy(ConfirmationMode.AUTO, null, 1, 10, 11, 30);

        assertDoesNotThrow(() -> policy.validateGenerationRange(TODAY.plusDays(5), TODAY.plusDays(29), TODAY));
        assertThrows(BookingRuleViolationException.class,
                () -> policy.validateGenerationRange(TODAY.plusDays(1), TODAY, TODAY));
        assertThrows(BookingRuleViolationException.class,
                () -> policy.validateGenerationRange(TODAY.minusDays(1), TODAY, TODAY));
        assertThrows(BookingRuleViolationException.class,
                () -> policy.validateGenerationRange(TODAY.plusDays(30), TODAY.plusDays(30), TODAY));
    }

    private BookingPolicy policy(
            ConfirmationMode mode, Integer hybridThreshold, int minimum, int maximumOnline, int largeThreshold, int windowDays) {
        return new BookingPolicy(20, 30, 30, mode, hybridThreshold, windowDays, minimum, maximumOnline, largeThreshold);
    }

    private BookingSlotCapacity capacity(String startsAt, int total, int reserved) {
        return new BookingSlotCapacity(Instant.parse(startsAt), total, reserved);
    }
}
