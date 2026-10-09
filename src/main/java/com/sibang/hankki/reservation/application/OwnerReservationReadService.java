package com.sibang.hankki.reservation.application;

import com.sibang.hankki.reservation.application.exception.ReservationNotFoundException;
import com.sibang.hankki.reservation.application.exception.InvalidReservationRequestException;
import com.sibang.hankki.reservation.application.port.in.OwnerReservationReadUseCase;
import com.sibang.hankki.reservation.application.port.in.OwnerReservationReadUseCase.OwnerReservationPage;
import com.sibang.hankki.reservation.application.port.in.OwnerReservationReadUseCase.OwnerReservationSummary;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort.OwnerReservationStatus;
import com.sibang.hankki.reservation.domain.model.Reservation;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerReservationReadService implements OwnerReservationReadUseCase {

    private final ReservationPersistencePort reservationPersistencePort;
    private final RestaurantCatalogPort restaurantCatalogPort;

    public OwnerReservationReadService(
            ReservationPersistencePort reservationPersistencePort,
            RestaurantCatalogPort restaurantCatalogPort) {
        this.reservationPersistencePort = reservationPersistencePort;
        this.restaurantCatalogPort = restaurantCatalogPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reservation> findAll(UUID restaurantId) {
        return reservationPersistencePort.findAllByRestaurantId(restaurantId);
    }

    @Override
    @Transactional(readOnly = true)
    public Reservation findById(UUID id, UUID restaurantId) {
        return reservationPersistencePort.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new ReservationNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public OwnerReservationPage findPage(
            UUID restaurantId,
            int page,
            int size,
            String query,
            String status,
            String dateFrom,
            String dateTo) {
        validatePage(page, size);
        String normalizedQuery = normalizeQuery(query);
        OwnerReservationStatus statusFilter = parseStatus(status);
        LocalDate from = parseDate(dateFrom, "dateFrom");
        LocalDate to = parseDate(dateTo, "dateTo");
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidReservationRequestException("dateFrom must not be after dateTo");
        }

        ZoneId timeZone = ZoneId.of(restaurantCatalogPort.findTimezoneById(restaurantId)
                .orElseThrow(() -> new ReservationNotFoundException(restaurantId)));
        var result = reservationPersistencePort.findOwnerPage(
                restaurantId,
                page,
                size,
                normalizedQuery,
                statusFilter,
                boundary(from, timeZone, false),
                boundary(to, timeZone, true));
        var summary = reservationPersistencePort.summarizeOwnerReservations(restaurantId);
        return new OwnerReservationPage(
                result.items(),
                page,
                size,
                result.totalElements(),
                result.totalPages(),
                new OwnerReservationSummary(
                        summary.confirmed(), summary.checkedIn(), summary.cancelled(), summary.noShow()));
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new InvalidReservationRequestException("page must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new InvalidReservationRequestException("size must be between 1 and 100");
        }
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.strip();
    }

    private OwnerReservationStatus parseStatus(String status) {
        if (status == null) {
            return null;
        }
        try {
            return OwnerReservationStatus.valueOf(status.strip());
        } catch (IllegalArgumentException exception) {
            throw new InvalidReservationRequestException("Invalid reservation status");
        }
    }

    private LocalDate parseDate(String value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new InvalidReservationRequestException(field + " must be an ISO local date");
        }
    }

    private Instant boundary(LocalDate date, ZoneId timeZone, boolean nextDay) {
        if (date == null) {
            return null;
        }
        try {
            return (nextDay ? date.plusDays(1) : date).atStartOfDay(timeZone).toInstant();
        } catch (DateTimeException exception) {
            throw new InvalidReservationRequestException("Date is outside the supported range");
        }
    }
}
