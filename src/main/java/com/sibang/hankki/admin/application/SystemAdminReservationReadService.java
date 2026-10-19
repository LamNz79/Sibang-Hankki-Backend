package com.sibang.hankki.admin.application;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.exception.InvalidAdminQueryException;
import com.sibang.hankki.admin.application.port.in.SystemAdminReservationReadUseCase;
import com.sibang.hankki.admin.application.port.out.SystemAdminReservationQueryPort;
import com.sibang.hankki.admin.application.port.out.SystemAdminReservationQueryPort.AdminReservationStatus;
import com.sibang.hankki.admin.application.port.out.SystemAdminRestaurantPort;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemAdminReservationReadService implements SystemAdminReservationReadUseCase {

    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(10, 20, 50, 100);

    private final SystemAdminReservationQueryPort queryPort;
    private final SystemAdminRestaurantPort restaurantPort;
    private final ZoneId reportingTimeZone;

    public SystemAdminReservationReadService(
            SystemAdminReservationQueryPort queryPort,
            SystemAdminRestaurantPort restaurantPort,
            @Value("${app.reporting.time-zone:Asia/Ho_Chi_Minh}") String reportingTimeZone) {
        this.queryPort = queryPort;
        this.restaurantPort = restaurantPort;
        try {
            this.reportingTimeZone = ZoneId.of(reportingTimeZone);
        } catch (DateTimeException exception) {
            throw new IllegalStateException("Invalid app.reporting.time-zone", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReservationPage findPage(
            int page,
            int size,
            String query,
            UUID restaurantId,
            String status,
            String dateFrom,
            String dateTo) {
        validatePage(page, size);
        String normalizedQuery = normalize(query);
        AdminReservationStatus statusFilter = parseStatus(status);
        LocalDate from = parseDate(dateFrom, "dateFrom");
        LocalDate to = parseDate(dateTo, "dateTo");
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidAdminQueryException("dateFrom must not be after dateTo");
        }
        if (restaurantId != null && restaurantPort.findById(restaurantId).isEmpty()) {
            throw new AdminResourceNotFoundException("Restaurant not found");
        }
        var result = queryPort.findPage(
                page,
                size,
                normalizedQuery,
                restaurantId,
                statusFilter,
                boundary(from, false),
                boundary(to, true));
        return new AdminReservationPage(
                result.items().stream().map(item -> new AdminReservation(
                        item.id(), item.reference(), item.restaurantId(), item.restaurantName(),
                        item.restaurantSlug(), item.customerName(), item.customerEmail(), item.customerPhone(),
                        item.startsAt(), item.endsAt(), item.partySize(), item.status(), item.visitStatus(),
                        item.createdAt(), item.updatedAt())).toList(),
                page,
                size,
                result.totalElements(),
                result.totalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReservationDetail findById(UUID reservationId) {
        var data = queryPort.findById(reservationId)
                .orElseThrow(() -> new AdminResourceNotFoundException("Reservation not found"));
        AlternativeProposal proposal = data.alternativeProposal() == null ? null : new AlternativeProposal(
                data.alternativeProposal().id(), data.alternativeProposal().startsAt(),
                data.alternativeProposal().message(), data.alternativeProposal().respondBy(),
                data.alternativeProposal().status(), data.alternativeProposal().initiator(),
                data.alternativeProposal().createdAt());
        return new AdminReservationDetail(
                data.id(), data.reference(), data.restaurantId(), data.restaurantName(), data.restaurantSlug(),
                data.customerName(), data.customerEmail(), data.customerPhone(), data.startsAt(), data.endsAt(),
                data.partySize(), data.status(), data.visitStatus(), data.specialRequest(), data.preOrderNote(),
                data.createdAt(), data.updatedAt(), proposal);
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new InvalidAdminQueryException("page must not be negative");
        }
        if (!ALLOWED_PAGE_SIZES.contains(size)) {
            throw new InvalidAdminQueryException("size must be one of 10, 20, 50, or 100");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private AdminReservationStatus parseStatus(String value) {
        if (value == null) {
            return null;
        }
        try {
            return AdminReservationStatus.valueOf(value.strip());
        } catch (IllegalArgumentException exception) {
            throw new InvalidAdminQueryException("Invalid status");
        }
    }

    private LocalDate parseDate(String value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new InvalidAdminQueryException(field + " must be an ISO local date");
        }
    }

    private Instant boundary(LocalDate date, boolean nextDay) {
        if (date == null) {
            return null;
        }
        try {
            return (nextDay ? date.plusDays(1) : date).atStartOfDay(reportingTimeZone).toInstant();
        } catch (DateTimeException exception) {
            throw new InvalidAdminQueryException("Date is outside the supported range");
        }
    }
}
