package com.sibang.hankki.reservation.adapter.in.web;

import com.sibang.hankki.auth.SessionUser;
import com.sibang.hankki.reservation.application.port.in.CustomerAccountReservationUseCase;
import com.sibang.hankki.reservation.application.port.in.CustomerAccountReservationUseCase.CustomerAccountReservation;
import com.sibang.hankki.reservation.domain.model.Reservation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer/account/reservations")
public class CustomerAccountReservationController {

    private final CustomerAccountReservationUseCase useCase;

    public CustomerAccountReservationController(CustomerAccountReservationUseCase useCase) {
        this.useCase = useCase;
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
        return CustomerAccountReservationResponse.from(useCase.findAccountReservation(id, user.id()));
    }

    @PostMapping("/{id}/cancel")
    public CustomerAccountReservationResponse cancel(
            @PathVariable UUID id, @AuthenticationPrincipal SessionUser user) {
        return CustomerAccountReservationResponse.from(useCase.cancelAccountReservation(id, user.id()));
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
            Instant updatedAt) {

        private static CustomerAccountReservationResponse from(CustomerAccountReservation view) {
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
                    reservation.updatedAt());
        }
    }
}
