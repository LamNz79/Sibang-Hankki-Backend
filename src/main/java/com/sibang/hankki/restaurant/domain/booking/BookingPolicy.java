package com.sibang.hankki.restaurant.domain.booking;

import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;
import java.time.LocalDate;
import java.util.List;

public record BookingPolicy(
        int guestCapacity,
        int bookingIntervalMinutes,
        int diningDurationMinutes,
        ConfirmationMode confirmationMode,
        Integer manualConfirmationMinPartySize,
        int bookingWindowDays,
        int minimumPartySize,
        int maximumOnlinePartySize,
        int largePartyThreshold) {

    public BookingPolicy {
        if (guestCapacity <= 0) {
            throw new BookingRuleViolationException("guestCapacity must be positive");
        }
        if (bookingIntervalMinutes <= 0) {
            throw new BookingRuleViolationException("bookingIntervalMinutes must be positive");
        }
        if (diningDurationMinutes <= 0) {
            throw new BookingRuleViolationException("diningDurationMinutes must be positive");
        }
        if (confirmationMode == null) {
            throw new BookingRuleViolationException("confirmationMode is required");
        }
        if (bookingWindowDays <= 0) {
            throw new BookingRuleViolationException("bookingWindowDays must be positive");
        }
        if (minimumPartySize <= 0) {
            throw new BookingRuleViolationException("minimumPartySize must be positive");
        }
        if (maximumOnlinePartySize < minimumPartySize) {
            throw new BookingRuleViolationException(
                    "maximumOnlinePartySize must be greater than or equal to minimumPartySize");
        }
        if (largePartyThreshold < maximumOnlinePartySize) {
            throw new BookingRuleViolationException(
                    "largePartyThreshold must be greater than or equal to maximumOnlinePartySize");
        }
        if (confirmationMode == ConfirmationMode.HYBRID) {
            if (manualConfirmationMinPartySize == null) {
                throw new BookingRuleViolationException(
                        "HYBRID confirmationMode requires manualConfirmationMinPartySize");
            }
            if (manualConfirmationMinPartySize <= 0) {
                throw new BookingRuleViolationException(
                        "manualConfirmationMinPartySize must be positive for HYBRID confirmationMode");
            }
        } else if (manualConfirmationMinPartySize != null) {
            throw new BookingRuleViolationException(
                    "AUTO and MANUAL confirmationMode must not define manualConfirmationMinPartySize");
        }
    }

    public void validateAvailabilityRequest(LocalDate date, LocalDate today, int partySize) {
        validateBasicRequest(date, today, partySize);
        if (partySize < minimumPartySize) {
            throw new BookingRuleViolationException("partySize is below the minimum");
        }
        if (date.isAfter(lastBookableDate(today))) {
            throw new BookingRuleViolationException("Date exceeds booking window");
        }
    }

    public static void validateBasicRequest(LocalDate date, LocalDate today, int partySize) {
        if (date == null) {
            throw new BookingRuleViolationException("date is required");
        }
        if (today == null) {
            throw new BookingRuleViolationException("today is required");
        }
        if (partySize <= 0) {
            throw new BookingRuleViolationException("partySize must be positive");
        }
        if (date.isBefore(today)) {
            throw new BookingRuleViolationException("Date must not be in the past");
        }
    }

    public void validateGenerationRange(LocalDate fromDate, LocalDate toDate, LocalDate today) {
        validateGenerationRequestShape(fromDate, toDate, today);
        if (toDate.isAfter(lastBookableDate(today))) {
            throw new BookingRuleViolationException("Requested range exceeds booking window");
        }
    }

    public static void validateGenerationRequestShape(LocalDate fromDate, LocalDate toDate, LocalDate today) {
        if (fromDate == null || toDate == null || today == null) {
            throw new BookingRuleViolationException("fromDate, toDate, and today are required");
        }
        if (fromDate.isAfter(toDate)) {
            throw new BookingRuleViolationException("fromDate must not be after toDate");
        }
        if (fromDate.isBefore(today)) {
            throw new BookingRuleViolationException("Cannot generate slots before today");
        }
    }

    public boolean supportsOnlineAvailability(int partySize) {
        return partySize <= maximumOnlinePartySize;
    }

    public boolean requiresRestaurantConfirmation(int partySize) {
        if (partySize >= largePartyThreshold) {
            return true;
        }
        return switch (confirmationMode) {
            case AUTO -> false;
            case MANUAL -> true;
            case HYBRID -> manualConfirmationMinPartySize != null && partySize >= manualConfirmationMinPartySize;
        };
    }

    public List<BookingSlotCapacity> availableSlots(List<BookingSlotCapacity> slots, int partySize) {
        return slots.stream()
                .filter(slot -> slot.capacityTotal() > 0
                        && slot.capacityTotal() - slot.capacityReserved() >= partySize)
                .toList();
    }


    private LocalDate lastBookableDate(LocalDate today) {
        return today.plusDays(bookingWindowDays - 1L);
    }
}
