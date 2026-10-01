package com.sibang.hankki.reservation.adapter.in.web;

import com.sibang.hankki.reservation.application.port.in.CreateReservationCommand;
import com.sibang.hankki.reservation.application.port.in.CreateReservationResult;
import com.sibang.hankki.reservation.application.port.in.CreateReservationUseCase;
import com.sibang.hankki.reservation.domain.model.Reservation;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
            @RequestBody CreateReservationRequest request) {
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
                request.preOrderNote()));
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

    public record ReservationResponse(
            String id,
            String reference,
            String restaurantSlug,
            String date,
            String time,
            int partySize,
            String status,
            boolean requiresRestaurantConfirmation,
            Instant createdAt) {

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
                    reservation.createdAt());
        }
    }
}
