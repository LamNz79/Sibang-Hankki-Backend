package com.sibang.hankki.reservation.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.reservation.application.exception.ReservationNotFoundException;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnerReservationReadServiceTest {

    @Mock
    private ReservationPersistencePort persistencePort;

    @InjectMocks
    private OwnerReservationReadService service;

    @Test
    void listUsesOnlyTheAuthenticatedRestaurantId() {
        UUID restaurantId = UUID.randomUUID();

        service.findAll(restaurantId);

        verify(persistencePort).findAllByRestaurantId(restaurantId);
    }

    @Test
    void crossRestaurantDetailIsNotFound() {
        UUID reservationId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        given(persistencePort.findByIdAndRestaurantId(reservationId, restaurantId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(reservationId, restaurantId))
                .isInstanceOf(ReservationNotFoundException.class);
    }
}
