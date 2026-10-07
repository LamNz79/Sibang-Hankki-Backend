package com.sibang.hankki.reservation.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sibang.hankki.auth.SessionUser;
import com.sibang.hankki.reservation.application.port.in.CreateReservationCommand;
import com.sibang.hankki.reservation.application.port.in.CreateReservationResult;
import com.sibang.hankki.reservation.application.port.in.CreateReservationUseCase;
import com.sibang.hankki.reservation.domain.model.Reservation;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final CreateReservationUseCase createReservationUseCase;

    public ReservationController(CreateReservationUseCase createReservationUseCase) {
        this.createReservationUseCase = createReservationUseCase;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateReservationRequest request,
            @AuthenticationPrincipal SessionUser user) {
        CreateReservationResult result = createReservationUseCase.create(new CreateReservationCommand(
                idempotencyKey,
                request.restaurantSlug(),
                request.date(),
                request.time(),
                request.partySize(),
                request.customerName(),
                request.customerPhone(),
                request.customerEmail(),
                request.specialRequest(),
                request.preOrderNote()),
                user != null && "CUSTOMER".equals(user.role()) ? user.id() : null);
        return ResponseEntity.status(result.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ReservationResponse.from(result));
    }

    public record CreateReservationRequest(
            String restaurantSlug,
            String date,
            String time,
            int partySize,
            String customerName,
            String customerPhone,
            String customerEmail,
            String specialRequest,
            String preOrderNote) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReservationResponse(
            String id,
            String reference,
            String restaurantSlug,
            String date,
            String time,
            int partySize,
            String status,
            boolean requiresRestaurantConfirmation,
            Instant createdAt,
            String managementToken) {

        private static ReservationResponse from(CreateReservationResult result) {
            Reservation reservation = result.reservation();
            return new ReservationResponse(
                    reservation.id().toString(),
                    reservation.reference(),
                    result.restaurantSlug(),
                    result.date().toString(),
                    result.time().toString(),
                    reservation.partySize(),
                    reservation.status().name(),
                    result.requiresRestaurantConfirmation(),
                    reservation.createdAt(),
                    result.managementToken());
        }

        @Override
        public String toString() {
            return "ReservationResponse[id=" + id + ", reference=" + reference
                    + ", managementToken=[REDACTED]]";
        }
    }
}
