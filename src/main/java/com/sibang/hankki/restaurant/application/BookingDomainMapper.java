package com.sibang.hankki.restaurant.application;

import com.sibang.hankki.restaurant.application.port.out.BookingSettings;
import com.sibang.hankki.restaurant.application.port.out.RestaurantBusinessHourData;
import com.sibang.hankki.restaurant.domain.booking.BookingPolicy;
import com.sibang.hankki.restaurant.domain.booking.BusinessPeriod;
import java.time.DayOfWeek;
import java.util.List;

public final class BookingDomainMapper {

    private BookingDomainMapper() {
    }

    public static BookingPolicy policy(BookingSettings settings) {
        return new BookingPolicy(
                settings.guestCapacity(),
                settings.bookingIntervalMinutes(),
                settings.diningDurationMinutes(),
                settings.confirmationMode(),
                settings.manualConfirmationMinPartySize(),
                settings.bookingWindowDays(),
                settings.minimumPartySize(),
                settings.maximumOnlinePartySize(),
                settings.largePartyThreshold());
    }

    public static List<BusinessPeriod> businessPeriods(List<RestaurantBusinessHourData> businessHours) {
        return businessHours.stream()
                .map(hours -> new BusinessPeriod(
                        DayOfWeek.of(hours.dayOfWeek()), hours.opensAt(), hours.closesAt()))
                .toList();
    }
}
