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
import com.sibang.hankki.reservation.domain.model.ReservationProposalInitiator;
import com.sibang.hankki.reservation.domain.model.ReservationProposalStatus;
import com.sibang.hankki.reservation.domain.model.ReservationStatus;
import com.sibang.hankki.reservation.domain.model.VisitStatus;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotData;
import com.sibang.hankki.restaurant.application.port.out.BookingSlotPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
import com.sibang.hankki.restaurant.domain.booking.BookingRuleViolationException;
import com.sibang.hankki.restaurant.domain.booking.BookingTime;
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
import java.util.Objects;
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
        ZoneId timeZone = restaurantTimeZone(command.restaurantId());
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
        if (slot.id().equals(reservation.bookingSlotId()) || slot.startsAt().equals(reservation.startsAt())) {
            throw new InvalidReservationStateException(
                    "Proposed time must differ from the requested reservation time");
        }
        if (!bookingSlotPort.hasAvailableCapacity(slot.id(), reservation.partySize())) {
            throw new ReservationCapacityUnavailableException();
        }

        Reservation proposedReservation = reservationPort.save(withScheduleAndStatus(
                reservation, reservation.bookingSlotId(), reservation.startsAt(), reservation.endsAt(),
                ReservationStatus.ALTERNATIVE_PROPOSED, null));
        ReservationProposal proposal = proposalPort.save(new ReservationProposal(
                UUID.randomUUID(), reservation.id(), slot.id(), slot.startsAt(), reservation.partySize(),
                proposedReservation.version(), message, respondBy, ReservationProposalStatus.PENDING,
                ReservationProposalInitiator.RESTAURANT, command.actorUserId(), now, null, null));
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
    @Transactional
    public Reservation requestAnotherTimeForAccount(
            UUID reservationId,
            UUID proposalId,
            UUID customerId,
            String requestedDate,
            String requestedTime) {
        requireActiveCustomer(customerId);
        Reservation reservation = reservationPort.findByIdAndCustomerIdForUpdate(reservationId, customerId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        return requestAnotherTime(reservation, proposalId, customerId, requestedDate, requestedTime);
    }

    @Override
    @Transactional
    public Reservation requestAnotherTimeForGuest(
            UUID reservationId,
            UUID proposalId,
            String managementToken,
            String requestedDate,
            String requestedTime) {
        Reservation reservation = reservationPort.findByIdAndManagementTokenHashForUpdate(
                        reservationId, managementTokenHash(reservationId, managementToken))
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        return requestAnotherTime(reservation, proposalId, null, requestedDate, requestedTime);
    }

    @Override
    @Transactional
    public ProposalResult requestChangeForAccount(
            UUID reservationId,
            UUID customerId,
            String requestedDate,
            String requestedTime,
            String message) {
        requireActiveCustomer(customerId);
        Reservation reservation = reservationPort.findByIdAndCustomerIdForUpdate(reservationId, customerId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        return requestChange(reservation, customerId, requestedDate, requestedTime, message);
    }

    @Override
    @Transactional
    public ProposalResult requestChangeForGuest(
            UUID reservationId,
            String managementToken,
            String requestedDate,
            String requestedTime,
            String message) {
        Reservation reservation = reservationPort.findByIdAndManagementTokenHashForUpdate(
                        reservationId, managementTokenHash(reservationId, managementToken))
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        return requestChange(reservation, null, requestedDate, requestedTime, message);
    }

    @Override
    @Transactional
    public ProposalResult acceptChangeRequest(
            UUID reservationId, UUID requestId, UUID restaurantId, UUID actorUserId) {
        Reservation reservation = reservationPort.findByIdAndRestaurantIdForUpdate(reservationId, restaurantId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        ReservationProposal proposal = proposalPort.findByIdAndReservationIdForUpdate(requestId, reservation.id())
                .orElseThrow(() -> new ReservationNotFoundException(requestId));
        if (isAcceptedChangeReplay(reservation, proposal)) {
            return new ProposalResult(reservation, proposal);
        }
        validateCustomerChangeRequest(reservation, proposal);
        Instant now = Instant.now(clock);
        if (!proposal.proposedStartsAt().isAfter(now)) {
            throw new InvalidReservationStateException("Requested time has already passed");
        }
        if (reservation.bookingSlotId() == null
                || reservation.bookingSlotId().equals(proposal.proposedBookingSlotId())) {
            throw new InvalidReservationStateException("Requested time must differ from the current reservation time");
        }
        BookingSlotData newSlot = bookingSlotPort
                .findByRestaurantIdAndStartsAt(restaurantId, proposal.proposedStartsAt())
                .filter(slot -> slot.id().equals(proposal.proposedBookingSlotId()))
                .orElseThrow(() -> new InvalidReservationStateException("Requested booking slot is stale"));
        lockSlots(restaurantId, reservation.bookingSlotId(), newSlot.id());
        if (!bookingSlotPort.hasAvailableCapacity(newSlot.id(), reservation.partySize())
                || !bookingSlotPort.reserveCapacity(newSlot.id(), reservation.partySize())) {
            throw new ReservationCapacityUnavailableException();
        }
        if (!bookingSlotPort.releaseCapacity(reservation.bookingSlotId(), reservation.partySize())) {
            throw new InvalidReservationStateException("Original reserved capacity could not be released");
        }

        Reservation updated = reservationPort.save(withScheduleAndStatus(
                reservation, newSlot.id(), newSlot.startsAt(), newSlot.endsAt(),
                ReservationStatus.CONFIRMED, VisitStatus.EXPECTED));
        ReservationProposal accepted = proposalPort.save(responded(
                proposal, ReservationProposalStatus.ACCEPTED, actorUserId, now));
        appendRescheduledEvent(reservation, newSlot.startsAt(), actorUserId, proposal.id(), now);
        return new ProposalResult(updated, accepted);
    }

    @Override
    @Transactional
    public ProposalResult declineChangeRequest(
            UUID reservationId, UUID requestId, UUID restaurantId, UUID actorUserId) {
        Reservation reservation = reservationPort.findByIdAndRestaurantIdForUpdate(reservationId, restaurantId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        ReservationProposal proposal = proposalPort.findByIdAndReservationIdForUpdate(requestId, reservation.id())
                .orElseThrow(() -> new ReservationNotFoundException(requestId));
        if (proposal.initiator() == ReservationProposalInitiator.CUSTOMER
                && proposal.status() == ReservationProposalStatus.DECLINED
                && reservation.status() == ReservationStatus.CONFIRMED
                && reservation.visitStatus() == VisitStatus.EXPECTED
                && reservation.version() == proposal.reservationVersion()) {
            return new ProposalResult(reservation, proposal);
        }
        validateCustomerChangeRequest(reservation, proposal);
        Instant now = Instant.now(clock);
        ReservationProposal declined = proposalPort.save(responded(
                proposal, ReservationProposalStatus.DECLINED, actorUserId, now));
        appendEvent(reservation.id(), ReservationEventType.RESCHEDULE_DECLINED,
                actorUserId, proposal.id(), now);
        return new ProposalResult(reservation, declined);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReservationProposal> findLatest(UUID reservationId) {
        return proposalPort.findLatestByReservationId(reservationId);
    }

    private ProposalResult accept(Reservation reservation, UUID proposalId, UUID actorUserId) {
        ReservationProposal proposal = lockAndValidate(
                reservation, proposalId, ReservationProposalInitiator.RESTAURANT);
        Instant now = Instant.now(clock);
        if (!now.isBefore(proposal.proposedStartsAt())) {
            throw new InvalidReservationStateException("Proposed time has already passed");
        }
        if (proposal.respondBy() != null && !now.isBefore(proposal.respondBy())) {
            throw new InvalidReservationStateException("Proposal response deadline has passed");
        }
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
        Reservation acceptedReservation = reservationPort.save(withScheduleAndStatus(
                reservation, slot.id(), slot.startsAt(), slot.endsAt(),
                ReservationStatus.CONFIRMED, VisitStatus.EXPECTED));
        ReservationProposal acceptedProposal = proposalPort.save(responded(
                proposal, ReservationProposalStatus.ACCEPTED, actorUserId, now));
        appendEvent(reservation.id(), ReservationEventType.ALTERNATIVE_ACCEPTED, actorUserId, proposal.id(), now);
        return new ProposalResult(acceptedReservation, acceptedProposal);
    }

    private ProposalResult decline(Reservation reservation, UUID proposalId, UUID actorUserId) {
        ReservationProposal proposal = lockAndValidate(
                reservation, proposalId, ReservationProposalInitiator.RESTAURANT);
        Instant now = Instant.now(clock);
        Reservation declinedReservation = reservationPort.save(withScheduleAndStatus(
                reservation, reservation.bookingSlotId(), reservation.startsAt(), reservation.endsAt(),
                ReservationStatus.DECLINED, null));
        ReservationProposal declinedProposal = proposalPort.save(responded(
                proposal, ReservationProposalStatus.DECLINED, actorUserId, now));
        appendEvent(reservation.id(), ReservationEventType.ALTERNATIVE_DECLINED, actorUserId, proposal.id(), now);
        return new ProposalResult(declinedReservation, declinedProposal);
    }

    private Reservation requestAnotherTime(
            Reservation reservation,
            UUID proposalId,
            UUID actorUserId,
            String requestedDate,
            String requestedTime) {
        ZoneId timeZone = restaurantTimeZone(reservation.restaurantId());
        Instant requestedStartsAt = parseStartsAt(requestedDate, requestedTime, timeZone, "requested");
        ReservationProposal proposal = proposalPort.findByIdAndReservationIdForUpdate(proposalId, reservation.id())
                .orElseThrow(() -> new ReservationNotFoundException(proposalId));

        if (isAnotherTimeReplay(reservation, proposal, requestedStartsAt)) {
            return reservation;
        }
        if (proposal.status() != ReservationProposalStatus.PENDING
                || proposal.initiator() != ReservationProposalInitiator.RESTAURANT
                || reservation.status() != ReservationStatus.ALTERNATIVE_PROPOSED
                || reservation.version() != proposal.reservationVersion()) {
            throw new InvalidReservationStateException("Reservation proposal is no longer current");
        }
        if (!requestedStartsAt.isAfter(Instant.now(clock))) {
            throw new InvalidReservationStateException("Requested time must be in the future");
        }

        BookingSlotData slot = bookingSlotPort
                .findByRestaurantIdAndStartsAt(reservation.restaurantId(), requestedStartsAt)
                .orElseThrow(ReservationSlotNotFoundException::new);
        if (slot.id().equals(proposal.proposedBookingSlotId())
                || slot.startsAt().equals(proposal.proposedStartsAt())) {
            throw new InvalidReservationStateException("Requested time must differ from the proposed time");
        }
        if (!bookingSlotPort.hasAvailableCapacity(slot.id(), reservation.partySize())) {
            throw new ReservationCapacityUnavailableException();
        }

        Instant now = Instant.now(clock);
        Reservation updated = reservationPort.save(withScheduleAndStatus(
                reservation, slot.id(), slot.startsAt(), slot.endsAt(), ReservationStatus.PENDING, null));
        proposalPort.save(responded(proposal, ReservationProposalStatus.DECLINED, actorUserId, now));
        appendAnotherTimeEvent(reservation, proposal, slot.startsAt(), actorUserId, now);
        return updated;
    }

    private ProposalResult requestChange(
            Reservation reservation,
            UUID actorUserId,
            String requestedDate,
            String requestedTime,
            String requestedMessage) {
        String message = normalizeMessage(requestedMessage);
        ZoneId timeZone = restaurantTimeZone(reservation.restaurantId());
        Instant requestedStartsAt = parseStartsAt(requestedDate, requestedTime, timeZone, "requested");
        if (!requestedStartsAt.isAfter(Instant.now(clock))) {
            throw new InvalidReservationStateException("Requested time must be in the future");
        }
        if (reservation.status() != ReservationStatus.PENDING
                && (reservation.status() != ReservationStatus.CONFIRMED
                        || reservation.visitStatus() != VisitStatus.EXPECTED)) {
            throw new InvalidReservationStateException(
                    "Only PENDING or CONFIRMED and EXPECTED reservations can be rescheduled");
        }

        BookingSlotData slot = bookingSlotPort
                .findByRestaurantIdAndStartsAt(reservation.restaurantId(), requestedStartsAt)
                .orElseThrow(ReservationSlotNotFoundException::new);
        boolean sameCurrentSlot = slot.id().equals(reservation.bookingSlotId())
                || slot.startsAt().equals(reservation.startsAt());
        if (reservation.status() == ReservationStatus.PENDING && sameCurrentSlot) {
            return new ProposalResult(reservation, null);
        }
        if (sameCurrentSlot) {
            throw new InvalidReservationStateException("Requested time must differ from the current reservation time");
        }
        Optional<ReservationProposal> pending = proposalPort.findPendingByReservationId(reservation.id());
        if (pending.isPresent()) {
            ReservationProposal existing = pending.get();
            if (reservation.status() == ReservationStatus.CONFIRMED
                    && existing.initiator() == ReservationProposalInitiator.CUSTOMER
                    && existing.reservationVersion() == reservation.version()
                    && existing.proposedBookingSlotId().equals(slot.id())
                    && Objects.equals(existing.message(), message)) {
                return new ProposalResult(reservation, existing);
            }
            throw new InvalidReservationStateException("Reservation already has a pending proposal");
        }
        if (!bookingSlotPort.hasAvailableCapacity(slot.id(), reservation.partySize())) {
            throw new ReservationCapacityUnavailableException();
        }

        Instant now = Instant.now(clock);
        if (reservation.status() == ReservationStatus.PENDING) {
            Reservation updated = reservationPort.save(withScheduleAndStatus(
                    reservation, slot.id(), slot.startsAt(), slot.endsAt(), ReservationStatus.PENDING, null));
            appendRescheduledEvent(reservation, slot.startsAt(), actorUserId, null, now);
            return new ProposalResult(updated, null);
        }

        ReservationProposal proposal = proposalPort.save(new ReservationProposal(
                UUID.randomUUID(), reservation.id(), slot.id(), slot.startsAt(), reservation.partySize(),
                reservation.version(), message, null, ReservationProposalStatus.PENDING,
                ReservationProposalInitiator.CUSTOMER, actorUserId, now, null, null));
        appendEvent(reservation.id(), ReservationEventType.ANOTHER_TIME_REQUESTED,
                actorUserId, proposal.id(), now);
        return new ProposalResult(reservation, proposal);
    }

    private boolean isAnotherTimeReplay(
            Reservation reservation, ReservationProposal proposal, Instant requestedStartsAt) {
        return reservation.status() == ReservationStatus.PENDING
                && proposal.status() == ReservationProposalStatus.DECLINED
                && reservation.version() == proposal.reservationVersion() + 1
                && reservation.startsAt().equals(requestedStartsAt);
    }

    private ReservationProposal lockAndValidate(
            Reservation reservation,
            UUID proposalId,
            ReservationProposalInitiator initiator) {
        ReservationProposal proposal = proposalPort.findByIdAndReservationIdForUpdate(proposalId, reservation.id())
                .orElseThrow(() -> new ReservationNotFoundException(proposalId));
        if (proposal.status() != ReservationProposalStatus.PENDING
                || proposal.initiator() != initiator
                || reservation.status() != ReservationStatus.ALTERNATIVE_PROPOSED
                || reservation.version() != proposal.reservationVersion()) {
            throw new InvalidReservationStateException("Reservation proposal is no longer current");
        }
        return proposal;
    }

    private void validateCustomerChangeRequest(
            Reservation reservation, ReservationProposal proposal) {
        if (proposal.initiator() != ReservationProposalInitiator.CUSTOMER
                || proposal.status() != ReservationProposalStatus.PENDING
                || reservation.status() != ReservationStatus.CONFIRMED
                || reservation.visitStatus() != VisitStatus.EXPECTED
                || reservation.version() != proposal.reservationVersion()) {
            throw new InvalidReservationStateException("Reservation change request is no longer current");
        }
    }

    private boolean isAcceptedChangeReplay(
            Reservation reservation, ReservationProposal proposal) {
        return proposal.initiator() == ReservationProposalInitiator.CUSTOMER
                && proposal.status() == ReservationProposalStatus.ACCEPTED
                && reservation.status() == ReservationStatus.CONFIRMED
                && reservation.visitStatus() == VisitStatus.EXPECTED
                && reservation.version() == proposal.reservationVersion() + 1
                && proposal.proposedBookingSlotId().equals(reservation.bookingSlotId())
                && reservation.startsAt().equals(proposal.proposedStartsAt());
    }

    private void lockSlots(UUID restaurantId, UUID firstSlotId, UUID secondSlotId) {
        UUID lower = firstSlotId.compareTo(secondSlotId) < 0 ? firstSlotId : secondSlotId;
        UUID higher = lower.equals(firstSlotId) ? secondSlotId : firstSlotId;
        if (!bookingSlotPort.lockByIdAndRestaurantId(lower, restaurantId)
                || !bookingSlotPort.lockByIdAndRestaurantId(higher, restaurantId)) {
            throw new InvalidReservationStateException("Reservation has a stale booking slot");
        }
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
                proposal.message(), proposal.respondBy(), status, proposal.initiator(),
                proposal.createdBy(), proposal.createdAt(),
                respondedBy, respondedAt);
    }

    private void appendEvent(
            UUID reservationId, ReservationEventType type, UUID actorUserId, UUID proposalId, Instant createdAt) {
        eventPort.append(new ReservationEvent(
                UUID.randomUUID(), reservationId, type, actorUserId,
                "proposal-" + type.name().toLowerCase() + ":" + proposalId,
                null, null, createdAt));
    }

    private void appendAnotherTimeEvent(
            Reservation reservation,
            ReservationProposal proposal,
            Instant requestedStartsAt,
            UUID actorUserId,
            Instant createdAt) {
        String metadata = "{\"previousRequestedStartsAt\":\"" + reservation.startsAt()
                + "\",\"rejectedProposedStartsAt\":\"" + proposal.proposedStartsAt()
                + "\",\"newlyRequestedStartsAt\":\"" + requestedStartsAt + "\"}";
        eventPort.append(new ReservationEvent(
                UUID.randomUUID(), reservation.id(), ReservationEventType.ANOTHER_TIME_REQUESTED, actorUserId,
                "proposal-another-time-requested:" + proposal.id(), null, metadata, createdAt));
    }

    private void appendRescheduledEvent(
            Reservation reservation,
            Instant requestedStartsAt,
            UUID actorUserId,
            UUID proposalId,
            Instant createdAt) {
        String metadata = "{\"previousStartsAt\":\"" + reservation.startsAt()
                + "\",\"newStartsAt\":\"" + requestedStartsAt + "\"}";
        String commandId = proposalId == null
                ? "customer-rescheduled:" + reservation.id() + ":" + reservation.version()
                : "proposal-rescheduled:" + proposalId;
        eventPort.append(new ReservationEvent(
                UUID.randomUUID(), reservation.id(), ReservationEventType.RESCHEDULED, actorUserId,
                commandId, null, metadata, createdAt));
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
        return parseStartsAt(date, time, timeZone, "proposed");
    }

    private ZoneId restaurantTimeZone(UUID restaurantId) {
        String timezone = restaurantCatalogPort.findTimezoneById(restaurantId)
                .orElseThrow(() -> new ReservationNotFoundException(restaurantId));
        try {
            return BookingTime.restaurantTimeZone(timezone);
        } catch (BookingRuleViolationException exception) {
            throw new InvalidReservationRequestException(exception.getMessage());
        }
    }

    private Instant parseStartsAt(String date, String time, ZoneId timeZone, String fieldPrefix) {
        try {
            return LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(timeZone).toInstant();
        } catch (DateTimeException | NullPointerException exception) {
            throw new InvalidReservationRequestException(
                    fieldPrefix + "Date and " + fieldPrefix + "Time must use ISO date and time formats");
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
