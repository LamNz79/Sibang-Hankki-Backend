package com.sibang.hankki.reservation.adapter.out.persistence;

import com.sibang.hankki.reservation.adapter.out.persistence.entity.ReservationEntity;
import com.sibang.hankki.reservation.adapter.out.persistence.repository.ReservationJpaRepository;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort.OwnerReservationPageData;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort.OwnerReservationStatus;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort.OwnerReservationSummaryData;
import com.sibang.hankki.reservation.domain.model.Reservation;
import com.sibang.hankki.reservation.domain.model.ReservationStatus;
import com.sibang.hankki.reservation.domain.model.VisitStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
    public OwnerReservationPageData findOwnerPage(
            UUID restaurantId,
            int page,
            int size,
            String query,
            OwnerReservationStatus status,
            Instant startsAtFrom,
            Instant startsAtBefore) {
        Specification<ReservationEntity> specification = (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("restaurantId"), restaurantId));
            if (query != null) {
                String pattern = "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("reference")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("customerName")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("customerPhone")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("customerEmail")), pattern, '\\')));
            }
            if (status != null) {
                predicates.add(statusPredicate(status, root, criteriaBuilder));
            }
            if (startsAtFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("startsAt"), startsAtFrom));
            }
            if (startsAtBefore != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("startsAt"), startsAtBefore));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
        var pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("startsAt"), Sort.Order.asc("id")));
        var result = repository.findAll(specification, pageable);
        return new OwnerReservationPageData(
                result.getContent().stream().map(this::toReservation).toList(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public OwnerReservationSummaryData summarizeOwnerReservations(UUID restaurantId) {
        var summary = repository.summarizeOwnerReservations(restaurantId);
        return new OwnerReservationSummaryData(
                summary.getConfirmed(), summary.getCheckedIn(), summary.getCancelled(), summary.getNoShow());
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

    @Override
    public List<Reservation> findAllByCustomerId(UUID customerId) {
        return repository.findAllByCustomerIdOrderByStartsAtDescIdAsc(customerId).stream()
                .map(this::toReservation)
                .toList();
    }

    @Override
    public Optional<Reservation> findByIdAndCustomerId(UUID id, UUID customerId) {
        return repository.findByIdAndCustomerId(id, customerId).map(this::toReservation);
    }

    @Override
    public Optional<Reservation> findByIdAndCustomerIdForUpdate(UUID id, UUID customerId) {
        return repository.findByIdAndCustomerIdForUpdate(id, customerId).map(this::toReservation);
    }

    @Override
    public Optional<Reservation> findByCheckInTokenHashAndRestaurantIdForUpdate(
            String checkInTokenHash, UUID restaurantId) {
        return repository.findByCheckInTokenHashAndRestaurantIdForUpdate(checkInTokenHash, restaurantId)
                .map(this::toReservation);
    }

    private Predicate statusPredicate(
            OwnerReservationStatus status,
            jakarta.persistence.criteria.Root<ReservationEntity> root,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder) {
        return switch (status) {
            case PENDING -> root.get("status").in(
                    ReservationStatus.PENDING, ReservationStatus.ALTERNATIVE_PROPOSED);
            case CONFIRMED -> criteriaBuilder.and(
                    criteriaBuilder.equal(root.get("status"), ReservationStatus.CONFIRMED),
                    criteriaBuilder.equal(root.get("visitStatus"), VisitStatus.EXPECTED));
            case CHECKED_IN -> criteriaBuilder.and(
                    criteriaBuilder.equal(root.get("status"), ReservationStatus.CONFIRMED),
                    root.get("visitStatus").in(
                            VisitStatus.ARRIVED, VisitStatus.SEATED, VisitStatus.COMPLETED));
            case CANCELLED -> criteriaBuilder.equal(root.get("status"), ReservationStatus.CANCELLED);
            case DECLINED -> root.get("status").in(
                    ReservationStatus.DECLINED, ReservationStatus.EXPIRED);
            case NO_SHOW -> criteriaBuilder.equal(root.get("visitStatus"), VisitStatus.NO_SHOW);
        };
    }

    private String escapeLike(String query) {
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
