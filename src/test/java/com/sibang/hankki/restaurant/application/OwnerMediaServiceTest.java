package com.sibang.hankki.restaurant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.model.OwnerMedia;
import com.sibang.hankki.restaurant.application.port.out.OwnerMediaPort;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OwnerMediaServiceTest {

    private final OwnerMediaPort port = mock(OwnerMediaPort.class);
    private final OwnerMediaService service = new OwnerMediaService(port);

    @Test
    void validatesAndNormalizesImageBeforeCreating() {
        UUID restaurantId = UUID.randomUUID();
        OwnerMedia.Update normalized = new OwnerMedia.Update(
                "https://example.com/dining-room.jpg", "Dining room", 0);

        service.create(restaurantId, new OwnerMedia.Update(
                " https://example.com/dining-room.jpg ", " Dining room ", 0));

        verify(port).create(restaurantId, normalized);
    }

    @Test
    void rejectsNonHttpImageUrl() {
        InvalidBookingRequestException error = assertThrows(InvalidBookingRequestException.class,
                () -> service.create(UUID.randomUUID(), new OwnerMedia.Update("file:///secret.jpg", null, 0)));
        assertEquals("imageUrl must be a valid HTTP or HTTPS URL", error.getMessage());
    }
}
