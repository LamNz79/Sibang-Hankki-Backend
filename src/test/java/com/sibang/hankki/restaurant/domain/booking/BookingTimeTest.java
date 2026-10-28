package com.sibang.hankki.restaurant.domain.booking;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BookingTimeTest {

    @Test
    void rejectsMissingOrBlankPersistedTimezone() {
        assertThatThrownBy(() -> BookingTime.restaurantTimeZone(null))
                .isInstanceOf(BookingRuleViolationException.class);
        assertThatThrownBy(() -> BookingTime.restaurantTimeZone("   "))
                .isInstanceOf(BookingRuleViolationException.class);
    }
}
