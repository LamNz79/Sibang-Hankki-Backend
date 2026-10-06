package com.sibang.hankki.reservation.adapter.in.web;

import com.sibang.hankki.reservation.application.port.in.CustomerReservationUseCase;
import com.sibang.hankki.reservation.domain.model.Reservation;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer/reservations")
public class CustomerReservationController {

    static final String MANAGEMENT_TOKEN_HEADER = "X-Reservation-Management-Token";

    private final CustomerReservationUseCase useCase;

    public CustomerReservationController(CustomerReservationUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/{id}")
    public CustomerReservationResponse findById(
            @PathVariable UUID id,
            @RequestHeader(value = MANAGEMENT_TOKEN_HEADER, required = false) String managementToken) {
        return CustomerReservationResponse.from(useCase.findById(id, managementToken));
    }

    @PostMapping("/{id}/cancel")
    public CustomerReservationResponse cancel(
            @PathVariable UUID id,
            @RequestHeader(value = MANAGEMENT_TOKEN_HEADER, required = false) String managementToken) {
        return CustomerReservationResponse.from(useCase.cancel(id, managementToken));
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
            Instant updatedAt) {

        private static CustomerReservationResponse from(Reservation reservation) {
            return new CustomerReservationResponse(
                    reservation.id(),
                    reservation.reference(),
                    reservation.startsAt(),
                    reservation.endsAt(),
                    reservation.partySize(),
                    reservation.status().name(),
                    reservation.visitStatus() == null ? null : reservation.visitStatus().name(),
                    reservation.createdAt(),
                    reservation.updatedAt());
        }
    }
}
