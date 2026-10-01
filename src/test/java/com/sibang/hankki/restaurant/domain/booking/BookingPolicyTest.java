package com.sibang.hankki.restaurant.domain.booking;

import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

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

    @Test
    void rejectsNonPositiveGuestCapacity() {
        assertViolation("guestCapacity must be positive",
                () -> bookingPolicy(0, 30, 30, ConfirmationMode.AUTO, null, 30, 1, 6, 7));
    }

    @Test
    void rejectsZeroOrNegativeBookingIntervalWithoutInvokingTheGenerator() {
        assertViolation("bookingIntervalMinutes must be positive",
                () -> bookingPolicy(20, 0, 30, ConfirmationMode.AUTO, null, 30, 1, 6, 7));
        assertViolation("bookingIntervalMinutes must be positive",
                () -> bookingPolicy(20, -1, 30, ConfirmationMode.AUTO, null, 30, 1, 6, 7));
    }

    @Test
    void rejectsNonPositiveDiningDuration() {
        assertViolation("diningDurationMinutes must be positive",
                () -> bookingPolicy(20, 30, 0, ConfirmationMode.AUTO, null, 30, 1, 6, 7));
    }

    @Test
    void rejectsNonPositiveBookingWindow() {
        assertViolation("bookingWindowDays must be positive",
                () -> bookingPolicy(20, 30, 30, ConfirmationMode.AUTO, null, 0, 1, 6, 7));
    }

    @Test
    void rejectsNonPositiveMinimumPartySize() {
        assertViolation("minimumPartySize must be positive",
                () -> bookingPolicy(20, 30, 30, ConfirmationMode.AUTO, null, 30, 0, 6, 7));
    }

    @Test
    void rejectsMaximumOnlinePartySizeBelowMinimum() {
        assertViolation("maximumOnlinePartySize must be greater than or equal to minimumPartySize",
                () -> bookingPolicy(20, 30, 30, ConfirmationMode.AUTO, null, 30, 3, 2, 3));
    }

    @Test
    void rejectsLargePartyThresholdBelowMaximumOnlinePartySize() {
        assertViolation("largePartyThreshold must be greater than or equal to maximumOnlinePartySize",
                () -> bookingPolicy(20, 30, 30, ConfirmationMode.AUTO, null, 30, 1, 6, 5));
    }

    @Test
    void rejectsMissingOrNonPositiveHybridManualConfirmationThreshold() {
        assertViolation("HYBRID confirmationMode requires manualConfirmationMinPartySize",
                () -> bookingPolicy(20, 30, 30, ConfirmationMode.HYBRID, null, 30, 1, 6, 7));
        assertViolation("manualConfirmationMinPartySize must be positive for HYBRID confirmationMode",
                () -> bookingPolicy(20, 30, 30, ConfirmationMode.HYBRID, 0, 30, 1, 6, 7));
    }

    @Test
    void rejectsManualConfirmationThresholdForAutoAndManualModes() {
        assertViolation("AUTO and MANUAL confirmationMode must not define manualConfirmationMinPartySize",
                () -> bookingPolicy(20, 30, 30, ConfirmationMode.AUTO, 4, 30, 1, 6, 7));
        assertViolation("AUTO and MANUAL confirmationMode must not define manualConfirmationMinPartySize",
                () -> bookingPolicy(20, 30, 30, ConfirmationMode.MANUAL, 4, 30, 1, 6, 7));
    }

    @Test
    void rejectsBusinessPeriodWhoseOpeningTimeIsNotBeforeClosingTime() {
        assertViolation("opensAt must be before closesAt",
                () -> new BusinessPeriod(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(10, 0)));
        assertViolation("opensAt must be before closesAt",
                () -> new BusinessPeriod(DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(10, 0)));
    }

    private void assertViolation(String expectedMessage, Executable action) {
        BookingRuleViolationException exception = assertThrows(BookingRuleViolationException.class, action);
        assertEquals(expectedMessage, exception.getMessage());
    }

    private BookingPolicy bookingPolicy(
            int guestCapacity,
            int interval,
            int duration,
            ConfirmationMode mode,
            Integer hybridThreshold,
            int windowDays,
            int minimum,
            int maximumOnline,
            int largeThreshold) {
        return new BookingPolicy(
                guestCapacity, interval, duration, mode, hybridThreshold, windowDays, minimum, maximumOnline, largeThreshold);
    }

    private BookingPolicy policy(
            ConfirmationMode mode, Integer hybridThreshold, int minimum, int maximumOnline, int largeThreshold, int windowDays) {
        return new BookingPolicy(20, 30, 30, mode, hybridThreshold, windowDays, minimum, maximumOnline, largeThreshold);
    }

    private BookingSlotCapacity capacity(String startsAt, int total, int reserved) {
        return new BookingSlotCapacity(Instant.parse(startsAt), total, reserved);
    }
}
