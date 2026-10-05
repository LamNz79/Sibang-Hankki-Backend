package com.sibang.hankki.reservation.adapter.in.web;

import com.sibang.hankki.auth.SessionUser;
import com.sibang.hankki.reservation.application.port.in.OwnerReservationReadUseCase;
import com.sibang.hankki.reservation.domain.model.Reservation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owner/reservations")
public class OwnerReservationController {

    private final OwnerReservationReadUseCase readUseCase;

    public OwnerReservationController(OwnerReservationReadUseCase readUseCase) {
        this.readUseCase = readUseCase;
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

    public record OwnerReservationResponse(
            UUID id,
            String reference,
            String customerName,
            String customerEmail,
            String customerPhone,
            Instant startsAt,
            Instant endsAt,
            int partySize,
            String status,
            String visitStatus,
            String specialRequest,
            String preOrderNote,
            Instant createdAt,
            Instant updatedAt) {

        private static OwnerReservationResponse from(Reservation reservation) {
            return new OwnerReservationResponse(
                    reservation.id(),
                    reservation.reference(),
                    reservation.customerName(),
                    reservation.customerEmail(),
                    reservation.customerPhone(),
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
