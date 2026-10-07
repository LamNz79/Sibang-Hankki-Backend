package com.sibang.hankki.reservation.application.port.in;

import java.util.UUID;

public interface CreateReservationUseCase {

    CreateReservationResult create(CreateReservationCommand command);

    CreateReservationResult create(CreateReservationCommand command, UUID authenticatedCustomerId);
}
