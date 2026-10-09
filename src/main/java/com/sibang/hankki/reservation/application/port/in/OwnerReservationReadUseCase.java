package com.sibang.hankki.reservation.application.port.in;

import com.sibang.hankki.reservation.domain.model.Reservation;
import java.util.List;
import java.util.UUID;

public interface OwnerReservationReadUseCase {

    List<Reservation> findAll(UUID restaurantId);

    Reservation findById(UUID id, UUID restaurantId);

    OwnerReservationPage findPage(
            UUID restaurantId,
            int page,
            int size,
            String query,
            String status,
            String dateFrom,
            String dateTo);

    record OwnerReservationPage(
            List<Reservation> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            OwnerReservationSummary summary) {
    }

    record OwnerReservationSummary(long confirmed, long checkedIn, long cancelled, long noShow) {
    }
}
