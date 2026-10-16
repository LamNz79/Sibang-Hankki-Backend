package com.sibang.hankki.admin.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.port.out.SystemAdminRestaurantPort;
import com.sibang.hankki.admin.application.port.out.SystemAdminRestaurantPort.RestaurantProfileData;
import com.sibang.hankki.restaurant.application.model.OwnerMedia;
import com.sibang.hankki.restaurant.application.port.in.OwnerMediaUseCase;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SystemAdminMediaServiceTest {

    private final SystemAdminRestaurantPort restaurantPort =
            org.mockito.Mockito.mock(SystemAdminRestaurantPort.class);
    private final OwnerMediaUseCase mediaUseCase = org.mockito.Mockito.mock(OwnerMediaUseCase.class);
    private final SystemAdminMediaService service =
            new SystemAdminMediaService(restaurantPort, mediaUseCase);

    @Test
    void delegatesSelectedRestaurantToExistingMediaUseCase() {
        UUID restaurantId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        OwnerMedia.Update update = new OwnerMedia.Update(
                "https://example.com/restaurant.jpg", "Dining room", 0);
        given(restaurantPort.findById(restaurantId))
                .willReturn(Optional.of(restaurant(restaurantId)));

        service.get(restaurantId);
        service.create(restaurantId, update);
        service.update(restaurantId, mediaId, update);
        service.delete(restaurantId, mediaId);

        verify(mediaUseCase).get(restaurantId);
        verify(mediaUseCase).create(restaurantId, update);
        verify(mediaUseCase).update(restaurantId, mediaId, update);
        verify(mediaUseCase).delete(restaurantId, mediaId);
    }

    @Test
    void rejectsMissingOrArchivedRestaurantBeforeMediaAccess() {
        UUID restaurantId = UUID.randomUUID();
        given(restaurantPort.findById(restaurantId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(restaurantId))
                .isInstanceOf(AdminResourceNotFoundException.class);
        verify(mediaUseCase, never()).get(restaurantId);
    }

    private RestaurantProfileData restaurant(UUID id) {
        return new RestaurantProfileData(
                id, "restaurant", "Restaurant", null, null, null, "ho-chi-minh-city",
                null, null, null, null, null, "Asia/Ho_Chi_Minh", null, "DRAFT",
                Instant.EPOCH, Instant.EPOCH);
    }
}
