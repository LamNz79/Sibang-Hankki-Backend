package com.sibang.hankki.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.exception.InvalidAdminQueryException;
import com.sibang.hankki.admin.application.port.out.SystemAdminReservationQueryPort;
import com.sibang.hankki.admin.application.port.out.SystemAdminReservationQueryPort.AdminReservationData;
import com.sibang.hankki.admin.application.port.out.SystemAdminReservationQueryPort.AdminReservationDetailData;
import com.sibang.hankki.admin.application.port.out.SystemAdminReservationQueryPort.AdminReservationStatus;
import com.sibang.hankki.admin.application.port.out.SystemAdminReservationQueryPort.AlternativeProposalData;
import com.sibang.hankki.admin.application.port.out.SystemAdminReservationQueryPort.PageData;
import com.sibang.hankki.admin.application.port.out.SystemAdminRestaurantPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class SystemAdminReservationReadServiceTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID RESERVATION_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");

    @Mock
    private SystemAdminReservationQueryPort queryPort;

    @Mock
    private SystemAdminRestaurantPort restaurantPort;

    private SystemAdminReservationReadService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new SystemAdminReservationReadService(queryPort, restaurantPort, "Asia/Ho_Chi_Minh");
    }

    @Test
    void appliesReportingTimezoneBoundariesAndMapsThePage() {
        given(restaurantPort.findById(RESTAURANT_ID)).willReturn(Optional.of(restaurant()));
        given(queryPort.findPage(
                0, 20, "Minh", RESTAURANT_ID, AdminReservationStatus.CONFIRMED,
                Instant.parse("2026-09-30T17:00:00Z"), Instant.parse("2026-10-02T17:00:00Z")))
                .willReturn(new PageData(List.of(reservation()), 1, 1));

        var result = service.findPage(
                0, 20, "  Minh  ", RESTAURANT_ID, "CONFIRMED", "2026-10-01", "2026-10-02");

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).restaurantName()).isEqualTo("The Royal Pavilion");
        assertThat(result.totalElements()).isEqualTo(1);
        verify(queryPort).findPage(
                0, 20, "Minh", RESTAURANT_ID, AdminReservationStatus.CONFIRMED,
                Instant.parse("2026-09-30T17:00:00Z"), Instant.parse("2026-10-02T17:00:00Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "CONFIRMED", "CHECKED_IN", "COMPLETED", "CANCELLED", "DECLINED", "NO_SHOW"})
    void acceptsEverySupportedStatus(String status) {
        given(queryPort.findPage(0, 20, null, null, AdminReservationStatus.valueOf(status), null, null))
                .willReturn(new PageData(List.of(), 0, 0));

        service.findPage(0, 20, null, null, status, null, null);

        verify(queryPort).findPage(0, 20, null, null, AdminReservationStatus.valueOf(status), null, null);
    }

    @Test
    void rejectsInvalidPagingStatusDatesAndUnknownRestaurant() {
        assertThatThrownBy(() -> service.findPage(-1, 20, null, null, null, null, null))
                .isInstanceOf(InvalidAdminQueryException.class);
        assertThatThrownBy(() -> service.findPage(0, 25, null, null, null, null, null))
                .isInstanceOf(InvalidAdminQueryException.class);
        assertThatThrownBy(() -> service.findPage(0, 20, null, null, "UNKNOWN", null, null))
                .isInstanceOf(InvalidAdminQueryException.class);
        assertThatThrownBy(() -> service.findPage(0, 20, null, null, null, "2026/10/01", null))
                .isInstanceOf(InvalidAdminQueryException.class);
        assertThatThrownBy(() -> service.findPage(0, 20, null, null, null, "2026-10-02", "2026-10-01"))
                .isInstanceOf(InvalidAdminQueryException.class);
        given(restaurantPort.findById(RESTAURANT_ID)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.findPage(0, 20, null, RESTAURANT_ID, null, null, null))
                .isInstanceOf(AdminResourceNotFoundException.class);
    }

    @Test
    void missingDetailReturnsNotFound() {
        given(queryPort.findById(RESERVATION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(RESERVATION_ID))
                .isInstanceOf(AdminResourceNotFoundException.class);
    }

    @Test
    void detailIncludesExistingAlternativeProposal() {
        var proposal = new AlternativeProposalData(
                UUID.randomUUID(), Instant.parse("2026-10-02T11:30:00Z"), "Can you come later?",
                Instant.parse("2026-10-01T00:00:00Z"), "PENDING", "CUSTOMER",
                Instant.parse("2026-09-30T00:00:00Z"));
        var item = reservation();
        given(queryPort.findById(RESERVATION_ID)).willReturn(Optional.of(new AdminReservationDetailData(
                item.id(), item.reference(), item.restaurantId(), item.restaurantName(), item.restaurantSlug(),
                item.restaurantTimezone(), item.customerName(), item.customerEmail(), item.customerPhone(),
                item.startsAt(), item.endsAt(),
                item.partySize(), item.status(), item.visitStatus(), "Window seat", "No peanuts",
                item.createdAt(), item.updatedAt(), proposal)));

        var detail = service.findById(RESERVATION_ID);

        assertThat(detail.specialRequest()).isEqualTo("Window seat");
        assertThat(detail.preOrderNote()).isEqualTo("No peanuts");
        assertThat(detail.restaurantTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(detail.alternativeProposal().message()).isEqualTo("Can you come later?");
        assertThat(detail.alternativeProposal().initiator()).isEqualTo("CUSTOMER");
    }

    private AdminReservationData reservation() {
        return new AdminReservationData(
                RESERVATION_ID, "SHK-0001", RESTAURANT_ID, "The Royal Pavilion", "royal-pavilion",
                "Asia/Ho_Chi_Minh", "Minh Lam", "minh@example.com", "0900000000",
                Instant.parse("2026-10-01T11:30:00Z"), Instant.parse("2026-10-01T13:00:00Z"),
                2, "CONFIRMED", "EXPECTED", Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-01T00:00:00Z"));
    }

    private SystemAdminRestaurantPort.RestaurantProfileData restaurant() {
        return new SystemAdminRestaurantPort.RestaurantProfileData(
                RESTAURANT_ID, "royal-pavilion", "The Royal Pavilion", null, null, null,
                "ho-chi-minh", null, null, null, null, null, null, null, null, null,
                "Asia/Ho_Chi_Minh", null, "ACTIVE", Instant.EPOCH, Instant.EPOCH);
    }
}
