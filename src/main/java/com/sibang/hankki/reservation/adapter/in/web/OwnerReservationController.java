package com.sibang.hankki.reservation.adapter.in.web;

import com.sibang.hankki.auth.SessionUser;
import com.sibang.hankki.reservation.application.port.in.OwnerReservationCommandUseCase;
import com.sibang.hankki.reservation.application.port.in.OwnerReservationReadUseCase;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owner/reservations")
public class OwnerReservationController {

    private final OwnerReservationReadUseCase readUseCase;
    private final OwnerReservationCommandUseCase commandUseCase;

    public OwnerReservationController(
            OwnerReservationReadUseCase readUseCase, OwnerReservationCommandUseCase commandUseCase) {
        this.readUseCase = readUseCase;
        this.commandUseCase = commandUseCase;
    }

    @GetMapping
    public List<OwnerReservationResponse> findAll(@AuthenticationPrincipal SessionUser user) {
        return readUseCase.findAll(user.restaurantId()).stream().map(OwnerReservationResponse::from).toList();
    }

    @GetMapping("/{id}")
    public OwnerReservationResponse findById(
            @PathVariable UUID id, @AuthenticationPrincipal SessionUser user) {
        return OwnerReservationResponse.from(readUseCase.findById(id, user.restaurantId()));
    }

    @PostMapping("/{id}/confirm")
    public OwnerReservationResponse confirm(
            @PathVariable UUID id, @AuthenticationPrincipal SessionUser user) {
        return OwnerReservationResponse.from(commandUseCase.confirm(id, user.restaurantId(), user.id()));
    }

    @PostMapping("/{id}/decline")
    public OwnerReservationResponse decline(
            @PathVariable UUID id,
            @RequestBody(required = false) DeclineReservationRequest request,
            @AuthenticationPrincipal SessionUser user) {
        String reason = request == null ? null : request.reason();
        return OwnerReservationResponse.from(commandUseCase.decline(id, user.restaurantId(), user.id(), reason));
    }

    @PostMapping("/{id}/seat")
    public OwnerReservationResponse seat(
            @PathVariable UUID id, @AuthenticationPrincipal SessionUser user) {
        return OwnerReservationResponse.from(commandUseCase.seat(id, user.restaurantId(), user.id()));
    }

    @PostMapping("/{id}/complete")
    public OwnerReservationResponse complete(
            @PathVariable UUID id, @AuthenticationPrincipal SessionUser user) {
        return OwnerReservationResponse.from(commandUseCase.complete(id, user.restaurantId(), user.id()));
    }

    public record DeclineReservationRequest(String reason) {
    }

}
