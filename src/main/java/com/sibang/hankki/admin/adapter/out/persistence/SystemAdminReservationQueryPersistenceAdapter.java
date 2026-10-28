package com.sibang.hankki.admin.adapter.out.persistence;

import com.sibang.hankki.admin.adapter.out.persistence.repository.AdminRestaurantRepository;
import com.sibang.hankki.admin.application.port.out.SystemAdminReservationQueryPort;
import com.sibang.hankki.admin.application.port.out.SystemAdminReservationQueryPort.AdminReservationData;
import com.sibang.hankki.admin.application.port.out.SystemAdminReservationQueryPort.AdminReservationDetailData;
import com.sibang.hankki.admin.application.port.out.SystemAdminReservationQueryPort.AdminReservationStatus;
import com.sibang.hankki.admin.application.port.out.SystemAdminReservationQueryPort.AlternativeProposalData;
import com.sibang.hankki.admin.application.port.out.SystemAdminReservationQueryPort.PageData;
import com.sibang.hankki.reservation.adapter.out.persistence.entity.ReservationEntity;
import com.sibang.hankki.reservation.adapter.out.persistence.entity.ReservationProposalEntity;
import com.sibang.hankki.reservation.adapter.out.persistence.repository.ReservationJpaRepository;
import com.sibang.hankki.reservation.adapter.out.persistence.repository.ReservationProposalJpaRepository;
import com.sibang.hankki.reservation.domain.model.ReservationStatus;
import com.sibang.hankki.reservation.domain.model.VisitStatus;
import com.sibang.hankki.restaurant.adapter.out.persistence.entity.RestaurantEntity;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class SystemAdminReservationQueryPersistenceAdapter implements SystemAdminReservationQueryPort {

    private static final Sort ADMIN_RESERVATION_SORT = Sort.by(
            Sort.Order.desc("startsAt"), Sort.Order.desc("id"));

    private final ReservationJpaRepository reservationRepository;
    private final ReservationProposalJpaRepository proposalRepository;
    private final AdminRestaurantRepository restaurantRepository;

    public SystemAdminReservationQueryPersistenceAdapter(
            ReservationJpaRepository reservationRepository,
            ReservationProposalJpaRepository proposalRepository,
            AdminRestaurantRepository restaurantRepository) {
        this.reservationRepository = reservationRepository;
        this.proposalRepository = proposalRepository;
        this.restaurantRepository = restaurantRepository;
    }

    @Override
    public PageData findPage(
            int page,
            int size,
            String query,
            UUID restaurantId,
            AdminReservationStatus status,
            Instant startsAtFrom,
            Instant startsAtBefore) {
        Specification<ReservationEntity> specification = (root, criteriaQuery, criteriaBuilder) -> {
            var predicates = new ArrayList<Predicate>();
            if (restaurantId != null) {
                predicates.add(criteriaBuilder.equal(root.get("restaurantId"), restaurantId));
            }
            if (query != null) {
                String pattern = likePattern(query);
                Subquery<UUID> matchingRestaurants = criteriaQuery.subquery(UUID.class);
                var restaurant = matchingRestaurants.from(RestaurantEntity.class);
                matchingRestaurants.select(restaurant.get("id")).where(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(restaurant.get("name")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(restaurant.get("slug")), pattern, '\\')));
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("reference")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("customerName")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("customerPhone")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("customerEmail")), pattern, '\\'),
                        root.get("restaurantId").in(matchingRestaurants)));
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
        var result = reservationRepository.findAll(
                specification, PageRequest.of(page, size, ADMIN_RESERVATION_SORT));
        Map<UUID, RestaurantEntity> restaurants = restaurantsById(result.getContent());
        return new PageData(
                result.getContent().stream()
                        .map(reservation -> toData(reservation, restaurants.get(reservation.getRestaurantId())))
                        .toList(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public Optional<AdminReservationDetailData> findById(UUID reservationId) {
        return reservationRepository.findById(reservationId).flatMap(reservation ->
                restaurantRepository.findById(reservation.getRestaurantId()).map(restaurant -> {
                    AlternativeProposalData proposal = proposalRepository
                            .findFirstByReservationIdOrderByCreatedAtDescIdDesc(reservationId)
                            .map(this::toProposalData)
                            .orElse(null);
                    return toDetailData(reservation, restaurant, proposal);
                }));
    }

    private Map<UUID, RestaurantEntity> restaurantsById(java.util.List<ReservationEntity> reservations) {
        return restaurantRepository.findAllById(reservations.stream()
                        .map(ReservationEntity::getRestaurantId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(RestaurantEntity::getId, Function.identity()));
    }

    private Predicate statusPredicate(
            AdminReservationStatus status,
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
            case COMPLETED -> criteriaBuilder.and(
                    criteriaBuilder.equal(root.get("status"), ReservationStatus.CONFIRMED),
                    criteriaBuilder.equal(root.get("visitStatus"), VisitStatus.COMPLETED));
            case CANCELLED -> criteriaBuilder.equal(root.get("status"), ReservationStatus.CANCELLED);
            case DECLINED -> root.get("status").in(ReservationStatus.DECLINED, ReservationStatus.EXPIRED);
            case NO_SHOW -> criteriaBuilder.equal(root.get("visitStatus"), VisitStatus.NO_SHOW);
        };
    }

    private String likePattern(String query) {
        return "%" + query.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }

    private AdminReservationData toData(ReservationEntity reservation, RestaurantEntity restaurant) {
        return new AdminReservationData(
                reservation.getId(), reservation.getReference(), reservation.getRestaurantId(),
                restaurant.getName(), restaurant.getSlug(), restaurant.getTimezone(), reservation.getCustomerName(),
                reservation.getCustomerEmail(), reservation.getCustomerPhone(), reservation.getStartsAt(),
                reservation.getEndsAt(), reservation.getPartySize(), reservation.getStatus().name(),
                reservation.getVisitStatus() == null ? null : reservation.getVisitStatus().name(),
                reservation.getCreatedAt(), reservation.getUpdatedAt());
    }

    private AdminReservationDetailData toDetailData(
            ReservationEntity reservation,
            RestaurantEntity restaurant,
            AlternativeProposalData proposal) {
        AdminReservationData data = toData(reservation, restaurant);
        return new AdminReservationDetailData(
                data.id(), data.reference(), data.restaurantId(), data.restaurantName(), data.restaurantSlug(),
                data.restaurantTimezone(), data.customerName(), data.customerEmail(), data.customerPhone(),
                data.startsAt(), data.endsAt(),
                data.partySize(), data.status(), data.visitStatus(), reservation.getSpecialRequest(),
                reservation.getPreOrderNote(), data.createdAt(), data.updatedAt(), proposal);
    }

    private AlternativeProposalData toProposalData(ReservationProposalEntity proposal) {
        return new AlternativeProposalData(
                proposal.getId(), proposal.getProposedStartsAt(), proposal.getMessage(), proposal.getRespondBy(),
                proposal.getStatus().name(), proposal.getInitiator().name(), proposal.getCreatedAt());
    }
}
