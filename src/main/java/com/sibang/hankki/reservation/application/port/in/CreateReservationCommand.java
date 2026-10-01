package com.sibang.hankki.reservation.application.port.in;

public record CreateReservationCommand(
        String idempotencyKey,
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
