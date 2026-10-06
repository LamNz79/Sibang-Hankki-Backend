package com.sibang.hankki.reservation.application.port.out;

import com.sibang.hankki.reservation.domain.model.Reservation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationPersistencePort {

    void lockIdempotencyKey(String idempotencyKey);

    Reservation save(Reservation reservation);

    Optional<Reservation> findById(UUID id);

    Optional<Reservation> findByReference(String reference);

    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

    List<Reservation> findAllByRestaurantId(UUID restaurantId);

    Optional<Reservation> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    Optional<Reservation> findByIdAndRestaurantIdForUpdate(UUID id, UUID restaurantId);
}
