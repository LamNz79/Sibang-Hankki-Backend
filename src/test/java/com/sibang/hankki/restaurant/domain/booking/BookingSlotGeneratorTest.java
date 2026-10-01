package com.sibang.hankki.restaurant.domain.booking;

import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BookingSlotGeneratorTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);
    private final BookingSlotGenerator generator = new BookingSlotGenerator();

    @Test
    void includesSlotWhoseDiningPeriodEndsExactlyAtClosingTime() {
        List<BookingSlotCandidate> slots = generate(List.of(period("10:00", "11:00")), 30, 30);

        assertEquals(List.of(
                        Instant.parse("2026-09-28T03:00:00Z"),
                        Instant.parse("2026-09-28T03:30:00Z")),
                slots.stream().map(BookingSlotCandidate::startsAt).toList());
    }

    @Test
    void excludesSlotWhoseDiningPeriodWouldEndAfterClosingTime() {
        List<BookingSlotCandidate> slots = generate(List.of(period("10:00", "10:45")), 15, 30);

        assertEquals(List.of(
                        Instant.parse("2026-09-28T03:00:00Z"),
                        Instant.parse("2026-09-28T03:15:00Z")),
                slots.stream().map(BookingSlotCandidate::startsAt).toList());
    }

    @Test
    void supportsSplitLunchAndDinnerPeriods() {
        List<BookingSlotCandidate> slots = generate(List.of(
                period("11:00", "12:00"),
                period("17:00", "18:00")), 30, 30);

        assertEquals(List.of(
                        Instant.parse("2026-09-28T04:00:00Z"),
                        Instant.parse("2026-09-28T04:30:00Z"),
                        Instant.parse("2026-09-28T10:00:00Z"),
                        Instant.parse("2026-09-28T10:30:00Z")),
                slots.stream().map(BookingSlotCandidate::startsAt).toList());
    }

    @Test
    void deduplicatesOverlappingPeriodsAndSortsSlotsByStartTime() {
        List<BookingSlotCandidate> slots = generate(List.of(
                period("10:30", "11:30"),
                period("10:00", "11:00")), 30, 30);

        assertEquals(List.of(
                        Instant.parse("2026-09-28T03:00:00Z"),
                        Instant.parse("2026-09-28T03:30:00Z"),
                        Instant.parse("2026-09-28T04:00:00Z")),
                slots.stream().map(BookingSlotCandidate::startsAt).toList());
    }

    @Test
    void returnsNoSlotsWhenThereAreNoBusinessHoursForTheDate() {
        BookingPolicy policy = policy(30, 30);

        assertEquals(List.of(), generator.generate(
                MONDAY, MONDAY, policy, List.of(new BusinessPeriod(DayOfWeek.TUESDAY, LocalTime.NOON, LocalTime.of(13, 0)))));
    }

    @Test
    void convertsHoChiMinhLocalTimeToInstant() {
        List<BookingSlotCandidate> slots = generate(List.of(period("11:30", "12:00")), 30, 30);

        assertEquals(Instant.parse("2026-09-28T04:30:00Z"), slots.get(0).startsAt());
        assertEquals(Instant.parse("2026-09-28T05:00:00Z"), slots.get(0).endsAt());
    }

    private List<BookingSlotCandidate> generate(List<BusinessPeriod> periods, int interval, int duration) {
        return generator.generate(MONDAY, MONDAY, policy(interval, duration), periods);
    }

    private BookingPolicy policy(int interval, int duration) {
        return new BookingPolicy(20, interval, duration, ConfirmationMode.AUTO, null, 30, 1, 10, 11);
    }

    private BusinessPeriod period(String opensAt, String closesAt) {
        return new BusinessPeriod(DayOfWeek.MONDAY, LocalTime.parse(opensAt), LocalTime.parse(closesAt));
    }
}
