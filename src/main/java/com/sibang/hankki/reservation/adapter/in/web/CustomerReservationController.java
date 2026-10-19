package com.sibang.hankki.reservation.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sibang.hankki.reservation.application.port.in.CustomerReservationUseCase;
import com.sibang.hankki.reservation.application.port.in.ReservationProposalUseCase;
import com.sibang.hankki.reservation.domain.model.Reservation;
import com.sibang.hankki.reservation.domain.model.ReservationProposal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer/reservations")
public class CustomerReservationController {

    static final String MANAGEMENT_TOKEN_HEADER = "X-Reservation-Management-Token";

    private final CustomerReservationUseCase useCase;
    private final ReservationProposalUseCase proposalUseCase;

    public CustomerReservationController(
            CustomerReservationUseCase useCase, ReservationProposalUseCase proposalUseCase) {
        this.useCase = useCase;
        this.proposalUseCase = proposalUseCase;
    }

    @GetMapping("/{id}")
    public CustomerReservationResponse findById(
            @PathVariable UUID id,
            @RequestHeader(value = MANAGEMENT_TOKEN_HEADER, required = false) String managementToken) {
        var reservation = useCase.findById(id, managementToken);
        return CustomerReservationResponse.from(
                reservation, proposalUseCase.findLatest(reservation.id()).orElse(null));
    }

    @PostMapping("/{id}/cancel")
    public CustomerReservationResponse cancel(
            @PathVariable UUID id,
            @RequestHeader(value = MANAGEMENT_TOKEN_HEADER, required = false) String managementToken) {
        return CustomerReservationResponse.from(useCase.cancel(id, managementToken));
    }

    @PostMapping("/{id}/check-in-token")
    public CheckInTokenResponse issueCheckInToken(
            @PathVariable UUID id,
            @RequestHeader(value = MANAGEMENT_TOKEN_HEADER, required = false) String managementToken) {
        return new CheckInTokenResponse(id, useCase.issueCheckInToken(id, managementToken));
    }

    @PostMapping("/{id}/proposals/{proposalId}/accept")
    public CustomerReservationResponse acceptProposal(
            @PathVariable UUID id,
            @PathVariable UUID proposalId,
            @RequestHeader(value = MANAGEMENT_TOKEN_HEADER, required = false) String managementToken) {
        var result = proposalUseCase.acceptForGuest(id, proposalId, managementToken);
        return CustomerReservationResponse.from(result.reservation(), result.proposal());
    }

    @PostMapping("/{id}/proposals/{proposalId}/decline")
    public CustomerReservationResponse declineProposal(
            @PathVariable UUID id,
            @PathVariable UUID proposalId,
            @RequestHeader(value = MANAGEMENT_TOKEN_HEADER, required = false) String managementToken) {
        var result = proposalUseCase.declineForGuest(id, proposalId, managementToken);
        return CustomerReservationResponse.from(result.reservation(), result.proposal());
    }

    @PostMapping("/{id}/proposals/{proposalId}/request-another-time")
    public CustomerReservationResponse requestAnotherTime(
            @PathVariable UUID id,
            @PathVariable UUID proposalId,
            @RequestHeader(value = MANAGEMENT_TOKEN_HEADER, required = false) String managementToken,
            @RequestBody RequestAnotherTimeRequest request) {
        return CustomerReservationResponse.from(proposalUseCase.requestAnotherTimeForGuest(
                id, proposalId, managementToken, request.requestedDate(), request.requestedTime()));
    }

    @PostMapping("/{id}/change-requests")
    public CustomerReservationResponse requestChange(
            @PathVariable UUID id,
            @RequestHeader(value = MANAGEMENT_TOKEN_HEADER, required = false) String managementToken,
            @RequestBody ReservationChangeRequest request) {
        var result = proposalUseCase.requestChangeForGuest(
                id, managementToken, request.requestedDate(), request.requestedTime(), request.message());
        return CustomerReservationResponse.from(result.reservation(), result.proposal());
    }

    public record CustomerReservationResponse(
            UUID id,
            String reference,
            Instant startsAt,
            Instant endsAt,
            int partySize,
            String status,
            String visitStatus,
            Instant createdAt,
            Instant updatedAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) AlternativeProposalResponse alternativeProposal) {

        private static CustomerReservationResponse from(Reservation reservation) {
            return from(reservation, null);
        }

        private static CustomerReservationResponse from(
                Reservation reservation, ReservationProposal proposal) {
            return new CustomerReservationResponse(
                    reservation.id(),
                    reservation.reference(),
                    reservation.startsAt(),
                    reservation.endsAt(),
                    reservation.partySize(),
                    reservation.status().name(),
                    reservation.visitStatus() == null ? null : reservation.visitStatus().name(),
                    reservation.createdAt(),
                    reservation.updatedAt(),
                    AlternativeProposalResponse.from(proposal));
        }
    }
}
