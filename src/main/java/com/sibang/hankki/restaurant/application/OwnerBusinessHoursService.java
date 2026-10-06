package com.sibang.hankki.restaurant.application;

import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.port.in.OwnerBusinessHoursUseCase;
import com.sibang.hankki.restaurant.application.port.in.OwnerSlotRegenerationUseCase;
import com.sibang.hankki.restaurant.application.port.out.OwnerBusinessHoursPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantBusinessHourData;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerBusinessHoursService implements OwnerBusinessHoursUseCase {

    private final OwnerBusinessHoursPort port;
    private final OwnerSlotRegenerationUseCase regenerationUseCase;

    public OwnerBusinessHoursService(
            OwnerBusinessHoursPort port, OwnerSlotRegenerationUseCase regenerationUseCase) {
        this.port = port;
        this.regenerationUseCase = regenerationUseCase;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RestaurantBusinessHourData> get(UUID restaurantId) {
        return port.findByRestaurantId(requireRestaurantId(restaurantId));
    }

    @Override
    @Transactional
    public UpdateResult update(UUID restaurantId, List<RestaurantBusinessHourData> hours) {
        UUID scopedRestaurantId = requireRestaurantId(restaurantId);
        List<RestaurantBusinessHourData> normalized = validateAndSort(scopedRestaurantId, hours);
        port.replace(scopedRestaurantId, normalized);
        return new UpdateResult(normalized, regenerationUseCase.regenerate(scopedRestaurantId));
    }

    private List<RestaurantBusinessHourData> validateAndSort(
            UUID restaurantId, List<RestaurantBusinessHourData> hours) {
        if (hours == null) {
            throw new InvalidBookingRequestException("hours are required");
        }
        List<RestaurantBusinessHourData> sorted = hours.stream()
                .map(hour -> new RestaurantBusinessHourData(
                        restaurantId, hour.dayOfWeek(), hour.opensAt(), hour.closesAt()))
                .sorted(Comparator.comparing(RestaurantBusinessHourData::dayOfWeek)
                        .thenComparing(RestaurantBusinessHourData::opensAt))
                .toList();
        for (int index = 0; index < sorted.size(); index++) {
            RestaurantBusinessHourData hour = sorted.get(index);
            if (hour.dayOfWeek() < 1 || hour.dayOfWeek() > 7
                    || hour.opensAt() == null || hour.closesAt() == null
                    || !hour.opensAt().isBefore(hour.closesAt())) {
                throw new InvalidBookingRequestException("business hours must use day 1-7 and open before close");
            }
            if (index > 0) {
                RestaurantBusinessHourData previous = sorted.get(index - 1);
                if (previous.dayOfWeek() == hour.dayOfWeek()
                        && hour.opensAt().isBefore(previous.closesAt())) {
                    throw new InvalidBookingRequestException("business hours must not overlap");
                }
            }
        }
        return sorted;
    }

    private UUID requireRestaurantId(UUID restaurantId) {
        if (restaurantId == null) {
            throw new InvalidBookingRequestException("restaurantId is required");
        }
        return restaurantId;
    }
}
