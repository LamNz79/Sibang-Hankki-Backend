package com.sibang.hankki.reservation.adapter.out.persistence;

import com.sibang.hankki.reservation.adapter.out.persistence.entity.ReservationEntity;
import com.sibang.hankki.reservation.adapter.out.persistence.repository.ReservationJpaRepository;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort;
import com.sibang.hankki.reservation.domain.model.Reservation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReservationPersistenceAdapter implements ReservationPersistencePort {

    private final ReservationJpaRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public ReservationPersistenceAdapter(ReservationJpaRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void lockIdempotencyKey(String idempotencyKey) {
        jdbcTemplate.queryForObject(
                "select pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class, idempotencyKey);
    }

    @Override
    public Reservation save(Reservation reservation) {
        ReservationEntity saved = repository.findById(reservation.id())
                .map(existing -> update(existing, reservation))
                .orElseGet(() -> repository.saveAndFlush(new ReservationEntity(reservation)));
        return toReservation(saved);
    }

    private ReservationEntity update(ReservationEntity existing, Reservation reservation) {
        if (existing.getVersion() != reservation.version()) {
            throw new OptimisticLockingFailureException(
                    "Reservation version does not match the persisted version");
        }
        existing.updateFrom(reservation);
        return repository.saveAndFlush(existing);
    }

    @Override
    public Optional<Reservation> findById(UUID id) {
        return repository.findById(id).map(this::toReservation);
    }

    @Override
    public Optional<Reservation> findByReference(String reference) {
        return repository.findByReference(reference).map(this::toReservation);
    }

    @Override
    public Optional<Reservation> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey).map(this::toReservation);
    }

    @Override
    public List<Reservation> findAllByRestaurantId(UUID restaurantId) {
        return repository.findAllByRestaurantIdOrderByStartsAtAscIdAsc(restaurantId).stream()
                .map(this::toReservation)
                .toList();
    }

    @Override
    public Optional<Reservation> findByIdAndRestaurantId(UUID id, UUID restaurantId) {
        return repository.findByIdAndRestaurantId(id, restaurantId).map(this::toReservation);
    }

    @Override
    public Optional<Reservation> findByIdAndRestaurantIdForUpdate(UUID id, UUID restaurantId) {
        return repository.findByIdAndRestaurantIdForUpdate(id, restaurantId).map(this::toReservation);
    }

    @Override
    public Optional<Reservation> findByIdAndManagementTokenHash(UUID id, String managementTokenHash) {
        return repository.findByIdAndManagementTokenHash(id, managementTokenHash).map(this::toReservation);
    }

    @Override
    public Optional<Reservation> findByIdAndManagementTokenHashForUpdate(UUID id, String managementTokenHash) {
        return repository.findByIdAndManagementTokenHashForUpdate(id, managementTokenHash).map(this::toReservation);
    }

    private Reservation toReservation(ReservationEntity entity) {
        return new Reservation(
                entity.getId(),
                entity.getReference(),
                entity.getIdempotencyKey(),
                entity.getRequestFingerprint(),
                entity.getRestaurantId(),
                entity.getBookingSlotId(),
                entity.getCustomerId(),
                entity.getCustomerName(),
                entity.getCustomerEmail(),
                entity.getCustomerPhone(),
                entity.getStartsAt(),
                entity.getEndsAt(),
                entity.getPartySize(),
                entity.getStatus(),
                entity.isCapacityOverride(),
                entity.getVisitStatus(),
                entity.getSpecialRequest(),
                entity.getPreOrderNote(),
                entity.getManagementTokenHash(),
                entity.getCheckInTokenHash(),
                entity.getCheckedInAt(),
                entity.getCheckedInBy(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
