package com.sibang.hankki.admin.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SystemAdminReservationQueryPort {

    PageData findPage(
            int page,
            int size,
            String query,
            UUID restaurantId,
            AdminReservationStatus status,
            Instant startsAtFrom,
            Instant startsAtBefore);

    Optional<AdminReservationDetailData> findById(UUID reservationId);

    enum AdminReservationStatus {
        PENDING,
        CONFIRMED,
        CHECKED_IN,
        COMPLETED,
        CANCELLED,
        DECLINED,
        NO_SHOW
    }

    record AdminReservationData(
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

    record AdminReservationDetailData(
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
            AlternativeProposalData alternativeProposal) {
    }

    record AlternativeProposalData(
            UUID id,
            Instant startsAt,
            String message,
            Instant respondBy,
            String status,
            String initiator,
            Instant createdAt) {
    }

    record PageData(List<AdminReservationData> items, long totalElements, int totalPages) {
    }
}
