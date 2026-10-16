package com.sibang.hankki.reservation.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sibang.hankki.auth.SessionUser;
import com.sibang.hankki.reservation.application.port.in.CustomerAccountReservationUseCase;
import com.sibang.hankki.reservation.application.port.in.CustomerAccountReservationUseCase.CustomerAccountReservation;
import com.sibang.hankki.reservation.application.port.in.ReservationProposalUseCase;
import com.sibang.hankki.reservation.domain.model.ReservationProposal;
import com.sibang.hankki.reservation.domain.model.Reservation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer/account/reservations")
public class CustomerAccountReservationController {

    private final CustomerAccountReservationUseCase useCase;
    private final ReservationProposalUseCase proposalUseCase;

    public CustomerAccountReservationController(
            CustomerAccountReservationUseCase useCase, ReservationProposalUseCase proposalUseCase) {
        this.useCase = useCase;
        this.proposalUseCase = proposalUseCase;
    }

    @GetMapping
    public List<CustomerAccountReservationResponse> findAll(@AuthenticationPrincipal SessionUser user) {
        return useCase.findAccountReservations(user.id()).stream()
                .map(CustomerAccountReservationResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public CustomerAccountReservationResponse findById(
            @PathVariable UUID id, @AuthenticationPrincipal SessionUser user) {
        var view = useCase.findAccountReservation(id, user.id());
        return CustomerAccountReservationResponse.from(
                view, proposalUseCase.findLatest(view.reservation().id()).orElse(null));
    }

    @PostMapping("/{id}/cancel")
    public CustomerAccountReservationResponse cancel(
            @PathVariable UUID id, @AuthenticationPrincipal SessionUser user) {
        return CustomerAccountReservationResponse.from(useCase.cancelAccountReservation(id, user.id()));
    }

    @PostMapping("/{id}/check-in-token")
    public CheckInTokenResponse issueCheckInToken(
            @PathVariable UUID id, @AuthenticationPrincipal SessionUser user) {
        return new CheckInTokenResponse(id, useCase.issueAccountCheckInToken(id, user.id()));
    }

    @PostMapping("/{id}/proposals/{proposalId}/accept")
    public CustomerAccountReservationResponse acceptProposal(
            @PathVariable UUID id,
            @PathVariable UUID proposalId,
            @AuthenticationPrincipal SessionUser user) {
        var result = proposalUseCase.acceptForAccount(id, proposalId, user.id());
        return CustomerAccountReservationResponse.from(
                useCase.findAccountReservation(result.reservation().id(), user.id()), result.proposal());
    }

    @PostMapping("/{id}/proposals/{proposalId}/decline")
    public CustomerAccountReservationResponse declineProposal(
            @PathVariable UUID id,
            @PathVariable UUID proposalId,
            @AuthenticationPrincipal SessionUser user) {
        var result = proposalUseCase.declineForAccount(id, proposalId, user.id());
        return CustomerAccountReservationResponse.from(
                useCase.findAccountReservation(result.reservation().id(), user.id()), result.proposal());
    }

    @PostMapping("/{id}/proposals/{proposalId}/request-another-time")
    public CustomerAccountReservationResponse requestAnotherTime(
            @PathVariable UUID id,
            @PathVariable UUID proposalId,
            @RequestBody RequestAnotherTimeRequest request,
            @AuthenticationPrincipal SessionUser user) {
        var reservation = proposalUseCase.requestAnotherTimeForAccount(
                id, proposalId, user.id(), request.requestedDate(), request.requestedTime());
        return CustomerAccountReservationResponse.from(
                useCase.findAccountReservation(reservation.id(), user.id()));
    }

    public record CustomerAccountReservationResponse(
            UUID id,
            String reference,
            String restaurantSlug,
            String restaurantName,
            Instant startsAt,
            Instant endsAt,
            int partySize,
            String status,
            String visitStatus,
            String specialRequest,
            String preOrderNote,
            Instant createdAt,
            Instant updatedAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) AlternativeProposalResponse alternativeProposal) {

        private static CustomerAccountReservationResponse from(CustomerAccountReservation view) {
            return from(view, null);
        }

        private static CustomerAccountReservationResponse from(
                CustomerAccountReservation view, ReservationProposal proposal) {
            Reservation reservation = view.reservation();
            return new CustomerAccountReservationResponse(
                    reservation.id(),
                    reservation.reference(),
                    view.restaurantSlug(),
                    view.restaurantName(),
                    reservation.startsAt(),
                    reservation.endsAt(),
                    reservation.partySize(),
                    reservation.status().name(),
                    reservation.visitStatus() == null ? null : reservation.visitStatus().name(),
                    reservation.specialRequest(),
                    reservation.preOrderNote(),
                    reservation.createdAt(),
                    reservation.updatedAt(),
                    AlternativeProposalResponse.from(proposal));
        }
    }
}
