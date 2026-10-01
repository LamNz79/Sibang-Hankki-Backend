package com.sibang.hankki.reservation.application.port.out;

import com.sibang.hankki.reservation.domain.model.Reservation;
import java.util.Optional;
import java.util.UUID;

public interface ReservationPersistencePort {

    Reservation save(Reservation reservation);

    Optional<Reservation> findById(UUID id);

    Optional<Reservation> findByReference(String reference);

    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);
}
