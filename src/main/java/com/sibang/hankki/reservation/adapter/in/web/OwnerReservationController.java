package com.sibang.hankki.reservation.adapter.in.web;

import com.sibang.hankki.auth.SessionUser;
import com.sibang.hankki.reservation.application.port.in.OwnerReservationCommandUseCase;
import com.sibang.hankki.reservation.application.port.in.OwnerReservationCommandUseCase.OwnerWalkInCommand;
import com.sibang.hankki.reservation.application.port.in.OwnerReservationReadUseCase;
import com.sibang.hankki.reservation.application.port.in.ReservationProposalUseCase;
import com.sibang.hankki.reservation.application.port.in.ReservationProposalUseCase.OwnerProposalCommand;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owner/reservations")
public class OwnerReservationController {

    private final OwnerReservationReadUseCase readUseCase;
    private final OwnerReservationCommandUseCase commandUseCase;
    private final ReservationProposalUseCase proposalUseCase;

    public OwnerReservationController(
            OwnerReservationReadUseCase readUseCase,
            OwnerReservationCommandUseCase commandUseCase,
            ReservationProposalUseCase proposalUseCase) {
        this.readUseCase = readUseCase;
        this.commandUseCase = commandUseCase;
        this.proposalUseCase = proposalUseCase;
    }

    @GetMapping
    public List<OwnerReservationResponse> findAll(@AuthenticationPrincipal SessionUser user) {
        String timeZone = readUseCase.restaurantTimezone(user.restaurantId());
        return readUseCase.findAll(user.restaurantId()).stream()
                .map(reservation -> OwnerReservationResponse.from(reservation, timeZone))
                .toList();
    }

    @PostMapping("/walk-ins")
    public ResponseEntity<OwnerReservationResponse> createWalkIn(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody OwnerWalkInRequest request,
            @AuthenticationPrincipal SessionUser user) {
        var result = commandUseCase.createWalkIn(new OwnerWalkInCommand(
                idempotencyKey,
                user.restaurantId(),
                user.id(),
                request.customerName(),
                request.customerPhone(),
                request.partySize(),
                request.specialRequest()));
        return ResponseEntity.status(result.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(OwnerReservationResponse.from(
                        result.reservation(), readUseCase.restaurantTimezone(user.restaurantId())));
    }

    @GetMapping("/paged")
    public OwnerReservationPageResponse findPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @AuthenticationPrincipal SessionUser user) {
        return OwnerReservationPageResponse.from(
                readUseCase.findPage(user.restaurantId(), page, size, query, status, dateFrom, dateTo),
                readUseCase.restaurantTimezone(user.restaurantId()));
    }

    @GetMapping("/{id}")
    public OwnerReservationResponse findById(
            @PathVariable UUID id, @AuthenticationPrincipal SessionUser user) {
        var reservation = readUseCase.findById(id, user.restaurantId());
        return OwnerReservationResponse.from(
                reservation, proposalUseCase.findLatest(reservation.id()).orElse(null),
                readUseCase.restaurantTimezone(user.restaurantId()));
    }

    @PostMapping("/{id}/proposals")
    public OwnerReservationResponse proposeAlternative(
            @PathVariable UUID id,
            @RequestBody AlternativeProposalRequest request,
            @AuthenticationPrincipal SessionUser user) {
        var result = proposalUseCase.create(new OwnerProposalCommand(
                id, user.restaurantId(), user.id(), request.proposedDate(), request.proposedTime(),
                request.message(), request.respondBy()));
        return OwnerReservationResponse.from(
                result.reservation(), result.proposal(), readUseCase.restaurantTimezone(user.restaurantId()));
    }

    @PostMapping("/{id}/change-requests/{requestId}/accept")
    public OwnerReservationResponse acceptChangeRequest(
            @PathVariable UUID id,
            @PathVariable UUID requestId,
            @AuthenticationPrincipal SessionUser user) {
        var result = proposalUseCase.acceptChangeRequest(
                id, requestId, user.restaurantId(), user.id());
        return OwnerReservationResponse.from(
                result.reservation(), result.proposal(), readUseCase.restaurantTimezone(user.restaurantId()));
    }

    @PostMapping("/{id}/change-requests/{requestId}/decline")
    public OwnerReservationResponse declineChangeRequest(
            @PathVariable UUID id,
            @PathVariable UUID requestId,
            @AuthenticationPrincipal SessionUser user) {
        var result = proposalUseCase.declineChangeRequest(
                id, requestId, user.restaurantId(), user.id());
        return OwnerReservationResponse.from(
                result.reservation(), result.proposal(), readUseCase.restaurantTimezone(user.restaurantId()));
    }

    @PostMapping("/{id}/confirm")
    public OwnerReservationResponse confirm(
            @PathVariable UUID id, @AuthenticationPrincipal SessionUser user) {
        return OwnerReservationResponse.from(
                commandUseCase.confirm(id, user.restaurantId(), user.id()),
                readUseCase.restaurantTimezone(user.restaurantId()));
    }

    @PostMapping("/{id}/decline")
    public OwnerReservationResponse decline(
            @PathVariable UUID id,
            @RequestBody(required = false) DeclineReservationRequest request,
            @AuthenticationPrincipal SessionUser user) {
        String reason = request == null ? null : request.reason();
        return OwnerReservationResponse.from(
                commandUseCase.decline(id, user.restaurantId(), user.id(), reason),
                readUseCase.restaurantTimezone(user.restaurantId()));
    }

    @PostMapping("/{id}/cancel")
    public OwnerReservationResponse cancel(
            @PathVariable UUID id,
            @RequestBody CancelReservationRequest request,
            @AuthenticationPrincipal SessionUser user) {
        return OwnerReservationResponse.from(
                commandUseCase.cancel(id, user.restaurantId(), user.id(), request.reason()),
                readUseCase.restaurantTimezone(user.restaurantId()));
    }

    @PostMapping("/{id}/check-in")
    public OwnerReservationResponse checkIn(
            @PathVariable UUID id, @AuthenticationPrincipal SessionUser user) {
        return OwnerReservationResponse.from(
                commandUseCase.checkIn(id, user.restaurantId(), user.id()),
                readUseCase.restaurantTimezone(user.restaurantId()));
    }

    @PostMapping("/{id}/seat")
    public OwnerReservationResponse seat(
            @PathVariable UUID id, @AuthenticationPrincipal SessionUser user) {
        return OwnerReservationResponse.from(
                commandUseCase.seat(id, user.restaurantId(), user.id()),
                readUseCase.restaurantTimezone(user.restaurantId()));
    }

    @PostMapping("/{id}/complete")
    public OwnerReservationResponse complete(
            @PathVariable UUID id, @AuthenticationPrincipal SessionUser user) {
        return OwnerReservationResponse.from(
                commandUseCase.complete(id, user.restaurantId(), user.id()),
                readUseCase.restaurantTimezone(user.restaurantId()));
    }

    @PostMapping("/{id}/no-show")
    public OwnerReservationResponse markNoShow(
            @PathVariable UUID id, @AuthenticationPrincipal SessionUser user) {
        return OwnerReservationResponse.from(
                commandUseCase.markNoShow(id, user.restaurantId(), user.id()),
                readUseCase.restaurantTimezone(user.restaurantId()));
    }

    public record DeclineReservationRequest(String reason) {
    }

    public record CancelReservationRequest(String reason) {
    }

    public record OwnerWalkInRequest(
            String customerName,
            String customerPhone,
            int partySize,
            String specialRequest) {
    }

    public record AlternativeProposalRequest(
            String proposedDate,
            String proposedTime,
            String message,
            String respondBy) {
    }

}
