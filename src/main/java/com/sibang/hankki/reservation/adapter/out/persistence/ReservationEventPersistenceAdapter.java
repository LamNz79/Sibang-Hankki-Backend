package com.sibang.hankki.reservation.adapter.out.persistence;

import com.sibang.hankki.reservation.adapter.out.persistence.entity.ReservationEventEntity;
import com.sibang.hankki.reservation.adapter.out.persistence.repository.ReservationEventJpaRepository;
import com.sibang.hankki.reservation.application.port.out.ReservationEventPersistencePort;
import com.sibang.hankki.reservation.domain.model.ReservationEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReservationEventPersistenceAdapter implements ReservationEventPersistencePort {

    private final ReservationEventJpaRepository repository;

    public ReservationEventPersistenceAdapter(ReservationEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public ReservationEvent append(ReservationEvent event) {
        return toReservationEvent(repository.saveAndFlush(new ReservationEventEntity(event)));
    }

    @Override
    public List<ReservationEvent> findByReservationId(UUID reservationId) {
        return repository.findByReservationIdOrderByCreatedAtAscIdAsc(reservationId).stream()
                .map(this::toReservationEvent)
                .toList();
    }

    private ReservationEvent toReservationEvent(ReservationEventEntity entity) {
        return new ReservationEvent(
                entity.getId(),
                entity.getReservationId(),
                entity.getEventType(),
                entity.getActorUserId(),
                entity.getCommandId(),
                entity.getRequestFingerprint(),
                entity.getMetadata(),
                entity.getCreatedAt());
    }
}
