package com.sibang.hankki.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.reservation.application.exception.InvalidReservationRequestException;
import com.sibang.hankki.reservation.application.exception.ReservationNotFoundException;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort.OwnerReservationPageData;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort.OwnerReservationStatus;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort.OwnerReservationSummaryData;
import com.sibang.hankki.reservation.application.port.out.ReservationPersistencePort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnerReservationReadServiceTest {

    @Mock
    private ReservationPersistencePort persistencePort;
    @Mock
    private RestaurantCatalogPort restaurantCatalogPort;

    @InjectMocks
    private OwnerReservationReadService service;

    @Test
    void listUsesOnlyTheAuthenticatedRestaurantId() {
        UUID restaurantId = UUID.randomUUID();

        service.findAll(restaurantId);

        verify(persistencePort).findAllByRestaurantId(restaurantId);
    }

    @Test
    void crossRestaurantDetailIsNotFound() {
        UUID reservationId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        given(persistencePort.findByIdAndRestaurantId(reservationId, restaurantId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(reservationId, restaurantId))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void pagedListNormalizesFiltersAndUsesRestaurantTimezone() {
        UUID restaurantId = UUID.randomUUID();
        given(restaurantCatalogPort.findTimezoneById(restaurantId))
                .willReturn(Optional.of("America/New_York"));
        given(persistencePort.findOwnerPage(
                restaurantId,
                0,
                20,
                "Minh",
                OwnerReservationStatus.CONFIRMED,
                Instant.parse("2026-10-01T04:00:00Z"),
                Instant.parse("2026-10-03T04:00:00Z")))
                .willReturn(new OwnerReservationPageData(List.of(), 0, 0));
        given(persistencePort.summarizeOwnerReservations(restaurantId))
                .willReturn(new OwnerReservationSummaryData(13, 3, 2, 1));

        var result = service.findPage(
                restaurantId, 0, 20, "  Minh  ", "CONFIRMED", "2026-10-01", "2026-10-02");

        assertThat(result.totalElements()).isZero();
        assertThat(result.summary().confirmed()).isEqualTo(13);
        assertThat(result.summary().checkedIn()).isEqualTo(3);
        assertThat(result.summary().cancelled()).isEqualTo(2);
        assertThat(result.summary().noShow()).isEqualTo(1);
    }

    @Test
    void pagedListValidatesPageAndSize() {
        UUID restaurantId = UUID.randomUUID();

        assertThatThrownBy(() -> service.findPage(restaurantId, -1, 20, null, null, null, null))
                .isInstanceOf(InvalidReservationRequestException.class);
        assertThatThrownBy(() -> service.findPage(restaurantId, 0, 0, null, null, null, null))
                .isInstanceOf(InvalidReservationRequestException.class);
        assertThatThrownBy(() -> service.findPage(restaurantId, 0, 101, null, null, null, null))
                .isInstanceOf(InvalidReservationRequestException.class);
    }

    @Test
    void pagedListRejectsInvalidStatusAndDates() {
        UUID restaurantId = UUID.randomUUID();

        assertThatThrownBy(() -> service.findPage(restaurantId, 0, 20, null, "ACTIVE", null, null))
                .isInstanceOf(InvalidReservationRequestException.class);
        assertThatThrownBy(() -> service.findPage(restaurantId, 0, 20, null, null, "2026-02-30", null))
                .isInstanceOf(InvalidReservationRequestException.class);
        assertThatThrownBy(() -> service.findPage(
                restaurantId, 0, 20, null, null, "2026-10-02", "2026-10-01"))
                .isInstanceOf(InvalidReservationRequestException.class);
    }
}
