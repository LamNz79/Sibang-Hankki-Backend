package com.sibang.hankki.restaurant.application;

import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;
import com.sibang.hankki.restaurant.application.model.OwnerSettings;
import com.sibang.hankki.restaurant.application.model.OwnerSettingsUpdate;
import com.sibang.hankki.restaurant.application.port.in.OwnerSettingsUseCase;
import com.sibang.hankki.restaurant.application.port.out.OwnerSettingsPort;
import com.sibang.hankki.restaurant.domain.booking.BookingPolicy;
import com.sibang.hankki.restaurant.domain.booking.BookingRuleViolationException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerSettingsService implements OwnerSettingsUseCase {

    private final OwnerSettingsPort port;

    public OwnerSettingsService(OwnerSettingsPort port) {
        this.port = port;
    }

    @Transactional(readOnly = true)
    @Override
    public OwnerSettings get(UUID restaurantId) {
        return port.findByRestaurantId(requireRestaurantId(restaurantId))
                .orElseThrow(RestaurantNotFoundException::new);
    }

    @Transactional
    @Override
    public OwnerSettings update(UUID restaurantId, OwnerSettingsUpdate update) {
        validate(update);
        return port.update(requireRestaurantId(restaurantId), normalize(update));
    }

    private void validate(OwnerSettingsUpdate update) {
        if (update == null || isBlank(update.name()) || update.name().trim().length() > 200) {
            throw new InvalidBookingRequestException("name is required and must be at most 200 characters");
        }
        validateLength(update.cuisineLabel(), 160, "cuisineLabel");
        validateLength(update.area(), 160, "area");
        validateLength(update.district(), 160, "district");
        validateLength(update.phone(), 30, "phone");
        validateLength(update.email(), 320, "email");
        validateLength(update.priceRange(), 40, "priceRange");
        if (update.customerCancellationCutoffMinutes() != null
                && update.customerCancellationCutoffMinutes() < 0) {
            throw new InvalidBookingRequestException("customerCancellationCutoffMinutes must not be negative");
        }
        validateSmallint(update.bookingIntervalMinutes(), "bookingIntervalMinutes");
        validateSmallint(update.diningDurationMinutes(), "diningDurationMinutes");
        validateSmallint(update.bookingWindowDays(), "bookingWindowDays");
        validateSmallint(update.minimumPartySize(), "minimumPartySize");
        validateSmallint(update.maximumOnlinePartySize(), "maximumOnlinePartySize");
        validateSmallint(update.largePartyThreshold(), "largePartyThreshold");
        if (update.manualConfirmationMinPartySize() != null) {
            validateSmallint(update.manualConfirmationMinPartySize(), "manualConfirmationMinPartySize");
        }
        try {
            new BookingPolicy(
                    update.guestCapacity(), update.bookingIntervalMinutes(), update.diningDurationMinutes(),
                    update.confirmationMode(), update.manualConfirmationMinPartySize(), update.bookingWindowDays(),
                    update.minimumPartySize(), update.maximumOnlinePartySize(), update.largePartyThreshold());
        } catch (BookingRuleViolationException exception) {
            throw new InvalidBookingRequestException(exception.getMessage());
        }
    }

    private OwnerSettingsUpdate normalize(OwnerSettingsUpdate update) {
        return new OwnerSettingsUpdate(
                update.name().trim(), trimToNull(update.description()), trimToNull(update.cuisineLabel()),
                trimToNull(update.area()), trimToNull(update.district()), trimToNull(update.address()),
                trimToNull(update.phone()), trimToNull(update.email()), trimToNull(update.priceRange()),
                update.guestCapacity(), update.bookingIntervalMinutes(), update.diningDurationMinutes(),
                update.confirmationMode(), update.manualConfirmationMinPartySize(), update.bookingWindowDays(),
                update.minimumPartySize(), update.maximumOnlinePartySize(), update.largePartyThreshold(),
                update.customerCancellationCutoffMinutes());
    }

    private UUID requireRestaurantId(UUID restaurantId) {
        if (restaurantId == null) {
            throw new RestaurantNotFoundException();
        }
        return restaurantId;
    }

    private void validateLength(String value, int maximum, String field) {
        if (value != null && value.trim().length() > maximum) {
            throw new InvalidBookingRequestException(field + " must be at most " + maximum + " characters");
        }
    }

    private void validateSmallint(int value, String field) {
        if (value > Short.MAX_VALUE) {
            throw new InvalidBookingRequestException(field + " exceeds the supported value");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
