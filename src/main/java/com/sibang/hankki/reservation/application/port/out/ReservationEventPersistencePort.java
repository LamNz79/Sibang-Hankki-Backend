package com.sibang.hankki.reservation.application.port.out;

import com.sibang.hankki.reservation.domain.model.ReservationEvent;
import java.util.List;
import java.util.UUID;

public interface ReservationEventPersistencePort {

    ReservationEvent append(ReservationEvent event);

    List<ReservationEvent> findByReservationId(UUID reservationId);
}
