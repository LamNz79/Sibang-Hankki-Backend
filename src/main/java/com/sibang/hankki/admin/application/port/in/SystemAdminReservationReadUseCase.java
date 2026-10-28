package com.sibang.hankki.admin.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SystemAdminReservationReadUseCase {

    AdminReservationPage findPage(
            int page,
            int size,
            String query,
            UUID restaurantId,
            String status,
            String dateFrom,
            String dateTo);

    AdminReservationDetail findById(UUID reservationId);

    record AdminReservation(
            UUID id,
            String reference,
            UUID restaurantId,
            String restaurantName,
            String restaurantSlug,
            String restaurantTimezone,
            String customerName,
            String customerEmail,
            String customerPhone,
            Instant startsAt,
            Instant endsAt,
            int partySize,
            String status,
            String visitStatus,
            Instant createdAt,
            Instant updatedAt) {
    }

    record AdminReservationDetail(
            UUID id,
            String reference,
            UUID restaurantId,
            String restaurantName,
            String restaurantSlug,
            String restaurantTimezone,
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
            Instant updatedAt,
            AlternativeProposal alternativeProposal) {
    }

    record AlternativeProposal(
            UUID id,
            Instant startsAt,
            String message,
            Instant respondBy,
            String status,
            String initiator,
            Instant createdAt) {
    }

    record AdminReservationPage(
            List<AdminReservation> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
