package com.sibang.hankki.restaurant.adapter.out.persistence;

import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantBookingSettings;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantEntity;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantBookingSettingsRepository;
import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantCatalogRepository;
import com.sibang.hankki.restaurant.application.exception.BookingSettingsNotConfiguredException;
import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;
import com.sibang.hankki.restaurant.application.model.OwnerSettings;
import com.sibang.hankki.restaurant.application.model.OwnerSettingsUpdate;
import com.sibang.hankki.restaurant.application.port.out.OwnerSettingsPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OwnerSettingsPersistenceAdapter implements OwnerSettingsPort {

    private final RestaurantCatalogRepository restaurantRepository;
    private final RestaurantBookingSettingsRepository bookingSettingsRepository;

    public OwnerSettingsPersistenceAdapter(
            RestaurantCatalogRepository restaurantRepository,
            RestaurantBookingSettingsRepository bookingSettingsRepository) {
        this.restaurantRepository = restaurantRepository;
        this.bookingSettingsRepository = bookingSettingsRepository;
    }

    @Override
    public Optional<OwnerSettings> findByRestaurantId(UUID restaurantId) {
        return restaurantRepository.findActiveById(restaurantId).map(restaurant -> {
            RestaurantBookingSettings settings = bookingSettingsRepository.findById(restaurantId)
                    .orElseThrow(BookingSettingsNotConfiguredException::new);
            return toOwnerSettings(restaurant, settings);
        });
    }

    @Override
    public OwnerSettings update(UUID restaurantId, OwnerSettingsUpdate update) {
        RestaurantEntity restaurant = restaurantRepository.findActiveById(restaurantId)
                .orElseThrow(RestaurantNotFoundException::new);
        RestaurantBookingSettings settings = bookingSettingsRepository.findById(restaurantId)
                .orElseThrow(BookingSettingsNotConfiguredException::new);
        restaurant.updateOwnerProfile(
                update.name(), update.description(), update.cuisineLabel(), update.area(), update.district(),
                update.address(), update.phone(), update.email(), update.priceRange());
        settings.updateOwnerSettings(
                update.guestCapacity(), update.bookingIntervalMinutes(), update.diningDurationMinutes(),
                update.confirmationMode(), update.manualConfirmationMinPartySize(), update.bookingWindowDays(),
                update.minimumPartySize(), update.maximumOnlinePartySize(), update.largePartyThreshold(),
                update.customerCancellationCutoffMinutes());
        return toOwnerSettings(restaurant, settings);
    }

    private OwnerSettings toOwnerSettings(RestaurantEntity restaurant, RestaurantBookingSettings settings) {
        return new OwnerSettings(
                restaurant.getId(), restaurant.getSlug(), restaurant.getName(), restaurant.getDescription(),
                restaurant.getCuisineLabel(), restaurant.getCitySlug(), restaurant.getArea(), restaurant.getDistrict(),
                restaurant.getAddress(), restaurant.getPhone(), restaurant.getEmail(), restaurant.getTimezone(),
                restaurant.getPriceRange(), settings.getGuestCapacity(), settings.getBookingIntervalMinutes(),
                settings.getDiningDurationMinutes(), settings.getConfirmationMode(),
                settings.getManualConfirmationMinPartySize() == null
                        ? null
                        : settings.getManualConfirmationMinPartySize().intValue(),
                settings.getBookingWindowDays(), settings.getMinimumPartySize(),
                settings.getMaximumOnlinePartySize(), settings.getLargePartyThreshold(),
                settings.getCustomerCancellationCutoffMinutes());
    }
}
