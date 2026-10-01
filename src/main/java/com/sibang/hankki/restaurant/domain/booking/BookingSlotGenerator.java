package com.sibang.hankki.restaurant.domain.booking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BookingSlotGenerator {

    public List<BookingSlotCandidate> generate(
            LocalDate fromDate,
            LocalDate toDate,
            BookingPolicy policy,
            List<BusinessPeriod> businessPeriods) {
        Objects.requireNonNull(fromDate, "fromDate is required");
        Objects.requireNonNull(toDate, "toDate is required");
        Objects.requireNonNull(policy, "policy is required");
        Objects.requireNonNull(businessPeriods, "businessPeriods is required");

        Map<java.time.Instant, BookingSlotCandidate> uniqueSlots = new LinkedHashMap<>();
        fromDate.datesUntil(toDate.plusDays(1))
                .flatMap(date -> businessPeriods.stream()
                        .filter(period -> period.dayOfWeek() == date.getDayOfWeek())
                        .flatMap(period -> slotsForPeriod(date, period, policy).stream()))
                .sorted(Comparator.comparing(BookingSlotCandidate::startsAt))
                .forEach(slot -> uniqueSlots.putIfAbsent(slot.startsAt(), slot));
        return List.copyOf(uniqueSlots.values());
    }

    private List<BookingSlotCandidate> slotsForPeriod(
            LocalDate date, BusinessPeriod period, BookingPolicy policy) {
        LocalDateTime closesAt = date.atTime(period.closesAt());
        java.util.ArrayList<BookingSlotCandidate> slots = new java.util.ArrayList<>();
        for (LocalDateTime startsAt = date.atTime(period.opensAt());
                !startsAt.plusMinutes(policy.diningDurationMinutes()).isAfter(closesAt);
                startsAt = startsAt.plusMinutes(policy.bookingIntervalMinutes())) {
            slots.add(new BookingSlotCandidate(
                    startsAt.atZone(BookingTime.RESTAURANT_TIME_ZONE).toInstant(),
                    startsAt.plusMinutes(policy.diningDurationMinutes())
                            .atZone(BookingTime.RESTAURANT_TIME_ZONE)
                            .toInstant()));
        }
        return slots;
    }
}
