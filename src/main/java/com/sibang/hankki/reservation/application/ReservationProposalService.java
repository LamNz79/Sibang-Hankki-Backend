package com.sibang.hankki.reservation.application;

import com.sibang.hankki.reservation.application.exception.InvalidReservationRequestException;
import com.sibang.hankki.reservation.application.exception.InvalidReservationStateException;
import com.sibang.hankki.reservation.application.exception.ReservationCapacityUnavailableException;
import com.sibang.hankki.reservation.application.exception.ReservationNotFoundException;
import com.sibang.hankki.reservation.application.exception.ReservationSlotNotFoundException;
import com.sibang.hankki.reservation.application.port.in.ReservationProposalUseCase;
import com.sibang.hankki.reservation.application.port.out.ReservationEventPersistencePort;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort;
import com.sibang.hankki.reservation.application.port.out.ReservationProposalPersistencePort;
import com.sibang.hankki.reservation.domain.model.Reservation;
import com.sibang.hankki.reservation.domain.model.ReservationEvent;
import com.sibang.hankki.reservation.domain.model.ReservationEventType;
import com.sibang.hankki.reservation.domain.model.ReservationProposal;
import com.sibang.hankki.reservation.domain.model.ReservationProposalStatus;
import com.sibang.hankki.reservation.domain.model.ReservationStatus;
import com.sibang.hankki.reservation.domain.model.VisitStatus;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotData;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
import com.sibang.hankki.user.application.exception.CustomerProfileNotFoundException;
import com.sibang.hankki.user.application.port.in.CustomerProfileUseCase;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationProposalService implements ReservationProposalUseCase {

    private final ReservationPersistencePort reservationPort;
    private final ReservationProposalPersistencePort proposalPort;
    private final ReservationEventPersistencePort eventPort;
    private final BookingSlotPort bookingSlotPort;
    private final RestaurantCatalogPort restaurantCatalogPort;
    private final CustomerProfileUseCase customerProfileUseCase;
    private final Clock clock;

    public ReservationProposalService(
            ReservationPersistencePort reservationPort,
            ReservationProposalPersistencePort proposalPort,
            ReservationEventPersistencePort eventPort,
            BookingSlotPort bookingSlotPort,
            RestaurantCatalogPort restaurantCatalogPort,
            CustomerProfileUseCase customerProfileUseCase,
            Clock clock) {
        this.reservationPort = reservationPort;
        this.proposalPort = proposalPort;
        this.eventPort = eventPort;
        this.bookingSlotPort = bookingSlotPort;
        this.restaurantCatalogPort = restaurantCatalogPort;
        this.customerProfileUseCase = customerProfileUseCase;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProposalResult create(OwnerProposalCommand command) {
        String message = normalizeMessage(command.message());
        Instant now = Instant.now(clock);
        ZoneId timeZone = restaurantCatalogPort.findTimezoneById(command.restaurantId())
                .map(ZoneId::of)
                .orElseThrow(() -> new ReservationNotFoundException(command.restaurantId()));
        Instant startsAt = parseProposedStartsAt(command.proposedDate(), command.proposedTime(), timeZone);
        if (!startsAt.isAfter(now)) {
            throw new InvalidReservationRequestException("Proposed time must be in the future");
        }
        Instant respondBy = parseRespondBy(command.respondBy());
        if (respondBy != null && (!respondBy.isAfter(now) || !respondBy.isBefore(startsAt))) {
            throw new InvalidReservationRequestException(
                    "respondBy must be in the future and before the proposed time");
        }

        Reservation reservation = reservationPort
                .findByIdAndRestaurantIdForUpdate(command.reservationId(), command.restaurantId())
                .orElseThrow(() -> new ReservationNotFoundException(command.reservationId()));
        if (reservation.status() != ReservationStatus.PENDING) {
            throw new InvalidReservationStateException("Reservation must be PENDING to receive a proposal");
        }
        if (proposalPort.findPendingByReservationId(reservation.id()).isPresent()) {
            throw new InvalidReservationStateException("Reservation already has a pending proposal");
        }
        BookingSlotData slot = bookingSlotPort.findByRestaurantIdAndStartsAt(command.restaurantId(), startsAt)
                .orElseThrow(ReservationSlotNotFoundException::new);
        if (!bookingSlotPort.hasAvailableCapacity(slot.id(), reservation.partySize())) {
            throw new ReservationCapacityUnavailableException();
        }

        Reservation proposedReservation = reservationPort.save(withScheduleAndStatus(
                reservation, reservation.bookingSlotId(), reservation.startsAt(), reservation.endsAt(),
                ReservationStatus.ALTERNATIVE_PROPOSED, null));
        ReservationProposal proposal = proposalPort.save(new ReservationProposal(
                UUID.randomUUID(), reservation.id(), slot.id(), slot.startsAt(), reservation.partySize(),
                proposedReservation.version(), message, respondBy, ReservationProposalStatus.PENDING,
                command.actorUserId(), now, null, null));
        appendEvent(reservation.id(), ReservationEventType.ALTERNATIVE_PROPOSED,
                command.actorUserId(), proposal.id(), now);
        return new ProposalResult(proposedReservation, proposal);
    }

    @Override
    @Transactional
    public ProposalResult acceptForAccount(UUID reservationId, UUID proposalId, UUID customerId) {
        requireActiveCustomer(customerId);
        Reservation reservation = reservationPort.findByIdAndCustomerIdForUpdate(reservationId, customerId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        return accept(reservation, proposalId, customerId);
    }

    @Override
    @Transactional
    public ProposalResult declineForAccount(UUID reservationId, UUID proposalId, UUID customerId) {
        requireActiveCustomer(customerId);
        Reservation reservation = reservationPort.findByIdAndCustomerIdForUpdate(reservationId, customerId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        return decline(reservation, proposalId, customerId);
    }

    @Override
    @Transactional
    public ProposalResult acceptForGuest(UUID reservationId, UUID proposalId, String managementToken) {
        Reservation reservation = reservationPort.findByIdAndManagementTokenHashForUpdate(
                        reservationId, managementTokenHash(reservationId, managementToken))
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        return accept(reservation, proposalId, null);
    }

    @Override
    @Transactional
    public ProposalResult declineForGuest(UUID reservationId, UUID proposalId, String managementToken) {
        Reservation reservation = reservationPort.findByIdAndManagementTokenHashForUpdate(
                        reservationId, managementTokenHash(reservationId, managementToken))
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        return decline(reservation, proposalId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReservationProposal> findLatest(UUID reservationId) {
        return proposalPort.findLatestByReservationId(reservationId);
    }

    private ProposalResult accept(Reservation reservation, UUID proposalId, UUID actorUserId) {
        ReservationProposal proposal = lockAndValidate(reservation, proposalId);
        if (!bookingSlotPort.lockByIdAndRestaurantId(
                proposal.proposedBookingSlotId(), reservation.restaurantId())) {
            throw new InvalidReservationStateException("Proposed booking slot is no longer available");
        }
        if (!bookingSlotPort.reserveCapacity(proposal.proposedBookingSlotId(), proposal.proposedPartySize())) {
            throw new ReservationCapacityUnavailableException();
        }
        BookingSlotData slot = bookingSlotPort
                .findByRestaurantIdAndStartsAt(reservation.restaurantId(), proposal.proposedStartsAt())
                .filter(candidate -> candidate.id().equals(proposal.proposedBookingSlotId()))
                .orElseThrow(() -> new InvalidReservationStateException("Proposed booking slot is stale"));
        Instant now = Instant.now(clock);
        Reservation acceptedReservation = reservationPort.save(withScheduleAndStatus(
                reservation, slot.id(), slot.startsAt(), slot.endsAt(),
                ReservationStatus.CONFIRMED, VisitStatus.EXPECTED));
        ReservationProposal acceptedProposal = proposalPort.save(responded(
                proposal, ReservationProposalStatus.ACCEPTED, actorUserId, now));
        appendEvent(reservation.id(), ReservationEventType.ALTERNATIVE_ACCEPTED, actorUserId, proposal.id(), now);
        return new ProposalResult(acceptedReservation, acceptedProposal);
    }

    private ProposalResult decline(Reservation reservation, UUID proposalId, UUID actorUserId) {
        ReservationProposal proposal = lockAndValidate(reservation, proposalId);
        Instant now = Instant.now(clock);
        Reservation declinedReservation = reservationPort.save(withScheduleAndStatus(
                reservation, reservation.bookingSlotId(), reservation.startsAt(), reservation.endsAt(),
                ReservationStatus.DECLINED, null));
        ReservationProposal declinedProposal = proposalPort.save(responded(
                proposal, ReservationProposalStatus.DECLINED, actorUserId, now));
        appendEvent(reservation.id(), ReservationEventType.ALTERNATIVE_DECLINED, actorUserId, proposal.id(), now);
        return new ProposalResult(declinedReservation, declinedProposal);
    }

    private ReservationProposal lockAndValidate(Reservation reservation, UUID proposalId) {
        ReservationProposal proposal = proposalPort.findByIdAndReservationIdForUpdate(proposalId, reservation.id())
                .orElseThrow(() -> new ReservationNotFoundException(proposalId));
        if (proposal.status() != ReservationProposalStatus.PENDING
                || reservation.status() != ReservationStatus.ALTERNATIVE_PROPOSED
                || reservation.version() != proposal.reservationVersion()) {
            throw new InvalidReservationStateException("Reservation proposal is no longer current");
        }
        return proposal;
    }

    private Reservation withScheduleAndStatus(
            Reservation reservation,
            UUID bookingSlotId,
            Instant startsAt,
            Instant endsAt,
            ReservationStatus status,
            VisitStatus visitStatus) {
        return new Reservation(
                reservation.id(), reservation.reference(), reservation.idempotencyKey(),
                reservation.requestFingerprint(), reservation.restaurantId(), bookingSlotId,
                reservation.customerId(), reservation.customerName(), reservation.customerEmail(),
                reservation.customerPhone(), startsAt, endsAt, reservation.partySize(), status,
                false, visitStatus, reservation.specialRequest(), reservation.preOrderNote(),
                reservation.managementTokenHash(), reservation.checkInTokenHash(), reservation.checkedInAt(),
                reservation.checkedInBy(), reservation.version(), reservation.createdAt(), reservation.updatedAt());
    }

    private ReservationProposal responded(
            ReservationProposal proposal,
            ReservationProposalStatus status,
            UUID respondedBy,
            Instant respondedAt) {
        return new ReservationProposal(
                proposal.id(), proposal.reservationId(), proposal.proposedBookingSlotId(),
                proposal.proposedStartsAt(), proposal.proposedPartySize(), proposal.reservationVersion(),
                proposal.message(), proposal.respondBy(), status, proposal.createdBy(), proposal.createdAt(),
                respondedBy, respondedAt);
    }

    private void appendEvent(
            UUID reservationId, ReservationEventType type, UUID actorUserId, UUID proposalId, Instant createdAt) {
        eventPort.append(new ReservationEvent(
                UUID.randomUUID(), reservationId, type, actorUserId,
                "proposal-" + type.name().toLowerCase() + ":" + proposalId,
                null, null, createdAt));
    }

    private void requireActiveCustomer(UUID customerId) {
        try {
            customerProfileUseCase.get(customerId);
        } catch (CustomerProfileNotFoundException exception) {
            throw new ReservationNotFoundException(customerId);
        }
    }

    private String managementTokenHash(UUID reservationId, String token) {
        if (token == null || token.isBlank()) {
            throw new ReservationNotFoundException(reservationId);
        }
        return ReservationToken.hash(token);
    }

    private String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String normalized = message.strip();
        if (normalized.length() > 1000) {
            throw new InvalidReservationRequestException("message must not exceed 1000 characters");
        }
        return normalized;
    }

    private Instant parseProposedStartsAt(String date, String time, ZoneId timeZone) {
        try {
            return LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(timeZone).toInstant();
        } catch (DateTimeException | NullPointerException exception) {
            throw new InvalidReservationRequestException(
                    "proposedDate and proposedTime must use ISO date and time formats");
        }
    }

    private Instant parseRespondBy(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.strip());
        } catch (DateTimeParseException exception) {
            throw new InvalidReservationRequestException("respondBy must be an ISO instant");
        }
    }
}
