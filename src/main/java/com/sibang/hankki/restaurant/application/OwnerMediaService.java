package com.sibang.hankki.restaurant.application;

import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.exception.OwnerMediaNotFoundException;
import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;
import com.sibang.hankki.restaurant.application.model.OwnerMedia;
import com.sibang.hankki.restaurant.application.port.in.OwnerMediaUseCase;
import com.sibang.hankki.restaurant.application.port.out.OwnerMediaPort;
import java.net.URI;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerMediaService implements OwnerMediaUseCase {

    private final OwnerMediaPort port;

    public OwnerMediaService(OwnerMediaPort port) {
        this.port = port;
    }

    @Override
    @Transactional(readOnly = true)
    public OwnerMedia get(UUID restaurantId) {
        return new OwnerMedia(port.findAll(requireRestaurantId(restaurantId)));
    }

    @Override
    @Transactional
    public OwnerMedia.Image create(UUID restaurantId, OwnerMedia.Update update) {
        return port.create(requireRestaurantId(restaurantId), validate(update));
    }

    @Override
    @Transactional
    public OwnerMedia.Image update(UUID restaurantId, UUID id, OwnerMedia.Update update) {
        return port.update(requireRestaurantId(restaurantId), id, validate(update))
                .orElseThrow(OwnerMediaNotFoundException::new);
    }

    @Override
    @Transactional
    public void delete(UUID restaurantId, UUID id) {
        if (!port.delete(requireRestaurantId(restaurantId), id)) {
            throw new OwnerMediaNotFoundException();
        }
    }

    private OwnerMedia.Update validate(OwnerMedia.Update update) {
        if (update == null || update.imageUrl() == null || update.imageUrl().isBlank()
                || update.imageUrl().trim().length() > 2_000) {
            throw new InvalidBookingRequestException("imageUrl is required and must be at most 2000 characters");
        }
        URI uri;
        try {
            uri = URI.create(update.imageUrl().trim());
        } catch (IllegalArgumentException exception) {
            throw new InvalidBookingRequestException("imageUrl must be a valid HTTP or HTTPS URL");
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) {
            throw new InvalidBookingRequestException("imageUrl must be a valid HTTP or HTTPS URL");
        }
        String altText = update.altText() == null || update.altText().isBlank() ? null : update.altText().trim();
        if (altText != null && altText.length() > 300) {
            throw new InvalidBookingRequestException("altText must be at most 300 characters");
        }
        if (update.sortOrder() < 0) {
            throw new InvalidBookingRequestException("sortOrder must not be negative");
        }
        return new OwnerMedia.Update(uri.toString(), altText, update.sortOrder());
    }

    private UUID requireRestaurantId(UUID restaurantId) {
        if (restaurantId == null) {
            throw new RestaurantNotFoundException();
        }
        return restaurantId;
    }
}
