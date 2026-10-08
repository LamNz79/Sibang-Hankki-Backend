package com.sibang.hankki.reservation.application.port.in;

import com.sibang.hankki.reservation.domain.model.Reservation;
import java.util.UUID;

public interface CustomerReservationUseCase {

    Reservation findById(UUID reservationId, String managementToken);

    Reservation cancel(UUID reservationId, String managementToken);

    String issueCheckInToken(UUID reservationId, String managementToken);
}
