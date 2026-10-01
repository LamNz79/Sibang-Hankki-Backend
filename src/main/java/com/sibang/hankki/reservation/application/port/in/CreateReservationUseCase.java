package com.sibang.hankki.reservation.application.port.in;

public interface CreateReservationUseCase {

    CreateReservationResult create(CreateReservationCommand command);
}
