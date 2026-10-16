package com.sibang.hankki.admin.application;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.port.in.SystemAdminMediaUseCase;
import com.sibang.hankki.admin.application.port.out.SystemAdminRestaurantPort;
import com.sibang.hankki.restaurant.application.model.OwnerMedia;
import com.sibang.hankki.restaurant.application.port.in.OwnerMediaUseCase;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SystemAdminMediaService implements SystemAdminMediaUseCase {

    private final SystemAdminRestaurantPort restaurantPort;
    private final OwnerMediaUseCase mediaUseCase;

    public SystemAdminMediaService(
            SystemAdminRestaurantPort restaurantPort, OwnerMediaUseCase mediaUseCase) {
        this.restaurantPort = restaurantPort;
        this.mediaUseCase = mediaUseCase;
    }

    @Override
    public OwnerMedia get(UUID restaurantId) {
        requireRestaurant(restaurantId);
        return mediaUseCase.get(restaurantId);
    }

    @Override
    public OwnerMedia.Image create(UUID restaurantId, OwnerMedia.Update update) {
        requireRestaurant(restaurantId);
        return mediaUseCase.create(restaurantId, update);
    }

    @Override
    public OwnerMedia.Image update(
            UUID restaurantId, UUID mediaId, OwnerMedia.Update update) {
        requireRestaurant(restaurantId);
        return mediaUseCase.update(restaurantId, mediaId, update);
    }

    @Override
    public void delete(UUID restaurantId, UUID mediaId) {
        requireRestaurant(restaurantId);
        mediaUseCase.delete(restaurantId, mediaId);
    }

    private void requireRestaurant(UUID restaurantId) {
        if (restaurantId == null || restaurantPort.findById(restaurantId).isEmpty()) {
            throw new AdminResourceNotFoundException("Restaurant not found");
        }
    }
}
