package com.sibang.hankki.reservation.application;

import com.sibang.hankki.reservation.application.exception.ReservationNotFoundException;
import com.sibang.hankki.reservation.application.port.in.OwnerReservationReadUseCase;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort;
import com.sibang.hankki.reservation.domain.model.Reservation;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerReservationReadService implements OwnerReservationReadUseCase {

    private final ReservationPersistencePort reservationPersistencePort;

    public OwnerReservationReadService(ReservationPersistencePort reservationPersistencePort) {
        this.reservationPersistencePort = reservationPersistencePort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reservation> findAll(UUID restaurantId) {
        return reservationPersistencePort.findAllByRestaurantId(restaurantId);
    }

    @Override
    @Transactional(readOnly = true)
    public Reservation findById(UUID id, UUID restaurantId) {
        return reservationPersistencePort.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new ReservationNotFoundException(id));
    }
}
