package com.sibang.hankki.restaurant.application;

import com.sibang.hankki.restaurant.adapter.out.persistence.entity.BookingSlot;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantBookingSettings;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantBusinessHourEntity;
import com.sibang.hankki.restaurant.domain.booking.BookingPolicy;
import com.sibang.hankki.restaurant.domain.booking.BookingSlotCapacity;
import com.sibang.hankki.restaurant.domain.booking.BusinessPeriod;
import java.time.DayOfWeek;
import java.util.List;

public final class BookingDomainMapper {

    private BookingDomainMapper() {
    }

    public static BookingPolicy policy(RestaurantBookingSettings settings) {
        return new BookingPolicy(
                settings.getGuestCapacity(),
                settings.getBookingIntervalMinutes(),
                settings.getDiningDurationMinutes(),
                settings.getConfirmationMode(),
                settings.getManualConfirmationMinPartySize() == null
                        ? null
                        : settings.getManualConfirmationMinPartySize().intValue(),
                settings.getBookingWindowDays(),
                settings.getMinimumPartySize(),
                settings.getMaximumOnlinePartySize(),
                settings.getLargePartyThreshold());
    }

    public static List<BusinessPeriod> businessPeriods(List<RestaurantBusinessHourEntity> businessHours) {
        return businessHours.stream()
                .map(hours -> new BusinessPeriod(
                        DayOfWeek.of(hours.getDayOfWeek()), hours.getOpensAt(), hours.getClosesAt()))
                .toList();
    }

    public static List<BookingSlotCapacity> slotCapacities(List<BookingSlot> slots) {
        return slots.stream()
                .map(slot -> new BookingSlotCapacity(
                        slot.getStartsAt(), slot.getCapacityTotal(), slot.getCapacityReserved()))
                .toList();
    }
}
