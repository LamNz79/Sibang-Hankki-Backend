package com.sibang.hankki.reservation.application.port.in;

import com.sibang.hankki.reservation.domain.model.Reservation;
import java.util.List;
import java.util.UUID;

public interface OwnerReservationReadUseCase {

    List<Reservation> findAll(UUID restaurantId);

    Reservation findById(UUID id, UUID restaurantId);
}
