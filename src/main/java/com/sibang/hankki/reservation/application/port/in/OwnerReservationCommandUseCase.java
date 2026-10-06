package com.sibang.hankki.reservation.application.port.in;

import com.sibang.hankki.reservation.domain.model.Reservation;
import java.util.UUID;

public interface OwnerReservationCommandUseCase {

    Reservation confirm(UUID reservationId, UUID restaurantId, UUID actorUserId);

    Reservation decline(UUID reservationId, UUID restaurantId, UUID actorUserId, String reason);
}
