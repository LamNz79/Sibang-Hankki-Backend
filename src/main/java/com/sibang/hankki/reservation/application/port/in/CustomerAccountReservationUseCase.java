package com.sibang.hankki.reservation.application.port.in;

import com.sibang.hankki.reservation.domain.model.Reservation;
import java.util.List;
import java.util.UUID;

public interface CustomerAccountReservationUseCase {

    List<CustomerAccountReservation> findAccountReservations(UUID customerId);

    CustomerAccountReservation findAccountReservation(UUID reservationId, UUID customerId);

    CustomerAccountReservation cancelAccountReservation(UUID reservationId, UUID customerId);

    record CustomerAccountReservation(
            Reservation reservation,
            String restaurantSlug,
            String restaurantName) {
    }
}
