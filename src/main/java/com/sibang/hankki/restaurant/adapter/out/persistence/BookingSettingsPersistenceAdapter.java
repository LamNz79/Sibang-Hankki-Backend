package com.sibang.hankki.restaurant.adapter.out.persistence;

import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantBookingSettings;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantBookingSettingsRepository;
import com.sibang.hankki.restaurant.application.port.out.BookingSettings;
import com.sibang.hankki.restaurant.application.port.out.BookingSettingsPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class BookingSettingsPersistenceAdapter implements BookingSettingsPort {

    private final RestaurantBookingSettingsRepository repository;

    public BookingSettingsPersistenceAdapter(RestaurantBookingSettingsRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<BookingSettings> findByRestaurantId(UUID restaurantId) {
        return repository.findById(restaurantId).map(this::toBookingSettings);
    }

    private BookingSettings toBookingSettings(RestaurantBookingSettings settings) {
        return new BookingSettings(
                settings.getRestaurantId(),
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
                settings.getLargePartyThreshold(),
                settings.getCustomerCancellationCutoffMinutes());
    }
}
