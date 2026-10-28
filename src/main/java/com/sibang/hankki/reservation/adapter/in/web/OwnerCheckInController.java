package com.sibang.hankki.reservation.adapter.in.web;

import com.sibang.hankki.auth.SessionUser;
import com.sibang.hankki.reservation.application.port.in.OwnerReservationCommandUseCase;
import com.sibang.hankki.reservation.application.port.in.OwnerReservationReadUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owner/check-ins")
public class OwnerCheckInController {

    private final OwnerReservationCommandUseCase useCase;
    private final OwnerReservationReadUseCase readUseCase;

    public OwnerCheckInController(
            OwnerReservationCommandUseCase useCase, OwnerReservationReadUseCase readUseCase) {
        this.useCase = useCase;
        this.readUseCase = readUseCase;
    }

    @PostMapping
    public OwnerReservationResponse checkIn(
            @RequestBody CheckInRequest request,
            @AuthenticationPrincipal SessionUser user) {
        return OwnerReservationResponse.from(
                useCase.checkIn(request.checkInToken(), user.restaurantId(), user.id()),
                readUseCase.restaurantTimezone(user.restaurantId()));
    }

    public record CheckInRequest(String checkInToken) {

        @Override
        public String toString() {
            return "CheckInRequest[checkInToken=[PROTECTED]]";
        }
    }
}
