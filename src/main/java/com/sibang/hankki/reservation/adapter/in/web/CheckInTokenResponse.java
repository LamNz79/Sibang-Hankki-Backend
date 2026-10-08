package com.sibang.hankki.reservation.adapter.in.web;

import java.util.UUID;

public record CheckInTokenResponse(UUID reservationId, String checkInToken) {

    @Override
    public String toString() {
        return "CheckInTokenResponse[reservationId=" + reservationId + ", checkInToken=[PROTECTED]]";
    }
}
