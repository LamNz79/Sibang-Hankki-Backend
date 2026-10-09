package com.sibang.hankki.reservation.application.port.in;

import com.sibang.hankki.reservation.domain.model.Reservation;
import java.util.UUID;

public interface OwnerReservationCommandUseCase {

    Reservation confirm(UUID reservationId, UUID restaurantId, UUID actorUserId);

    Reservation decline(UUID reservationId, UUID restaurantId, UUID actorUserId, String reason);

    Reservation cancel(UUID reservationId, UUID restaurantId, UUID actorUserId, String reason);

    Reservation checkIn(String checkInToken, UUID restaurantId, UUID actorUserId);

    Reservation checkIn(UUID reservationId, UUID restaurantId, UUID actorUserId);

    Reservation seat(UUID reservationId, UUID restaurantId, UUID actorUserId);

    Reservation complete(UUID reservationId, UUID restaurantId, UUID actorUserId);
}
