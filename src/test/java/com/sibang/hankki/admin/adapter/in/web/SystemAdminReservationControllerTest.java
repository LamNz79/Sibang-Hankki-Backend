package com.sibang.hankki.admin.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.exception.InvalidAdminQueryException;
import com.sibang.hankki.admin.application.port.in.SystemAdminReservationReadUseCase;
import com.sibang.hankki.admin.application.port.in.SystemAdminReservationReadUseCase.AdminReservation;
import com.sibang.hankki.admin.application.port.in.SystemAdminReservationReadUseCase.AdminReservationDetail;
import com.sibang.hankki.admin.application.port.in.SystemAdminReservationReadUseCase.AdminReservationPage;
import com.sibang.hankki.admin.application.port.in.SystemAdminReservationReadUseCase.AlternativeProposal;
import com.sibang.hankki.auth.config.SecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SystemAdminReservationController.class)
@Import({SecurityConfig.class, SystemAdminExceptionHandler.class})
class SystemAdminReservationControllerTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID RESERVATION_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemAdminReservationReadUseCase readUseCase;

    @Test
    void anonymousIsUnauthorizedAndNonAdminRolesAreForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/reservations"))
                .andExpect(status().isUnauthorized());
        for (String role : List.of("CUSTOMER", "OWNER", "STAFF")) {
            mockMvc.perform(get("/api/admin/reservations").with(user(role.toLowerCase()).roles(role)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/admin/reservations/{id}", RESERVATION_ID)
                            .with(user(role.toLowerCase()).roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminReadsPagedReservationsWithoutCsrfOrSecurityFields() throws Exception {
        given(readUseCase.findPage(0, 20, null, null, null, null, null))
                .willReturn(new AdminReservationPage(List.of(item()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/admin/reservations").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].restaurantId").value(RESTAURANT_ID.toString()))
                .andExpect(jsonPath("$.items[0].restaurantName").value("The Royal Pavilion"))
                .andExpect(jsonPath("$.items[0].restaurantSlug").value("royal-pavilion"))
                .andExpect(jsonPath("$.items[0].restaurantTimezone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.items[0].managementTokenHash").doesNotExist())
                .andExpect(jsonPath("$.items[0].checkInTokenHash").doesNotExist());
    }

    @Test
    void invalidFiltersReturnBadRequest() throws Exception {
        given(readUseCase.findPage(-1, 20, null, null, null, null, null))
                .willThrow(new InvalidAdminQueryException("invalid page"));
        given(readUseCase.findPage(0, 25, null, null, null, null, null))
                .willThrow(new InvalidAdminQueryException("invalid size"));
        given(readUseCase.findPage(0, 20, null, null, "UNKNOWN", null, null))
                .willThrow(new InvalidAdminQueryException("invalid status"));
        given(readUseCase.findPage(0, 20, null, null, null, "2026-10-02", "2026-10-01"))
                .willThrow(new InvalidAdminQueryException("invalid range"));

        for (String url : List.of(
                "/api/admin/reservations?page=-1",
                "/api/admin/reservations?size=25",
                "/api/admin/reservations?status=UNKNOWN",
                "/api/admin/reservations?dateFrom=2026-10-02&dateTo=2026-10-01",
                "/api/admin/reservations?restaurantId=not-a-uuid")) {
            mockMvc.perform(get(url).with(user("admin").roles("ADMIN")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void adminDetailExposesProposalInitiator() throws Exception {
        var item = item();
        var proposal = new AlternativeProposal(
                UUID.randomUUID(), NOW.plusSeconds(3600), "Later please", null,
                "PENDING", "CUSTOMER", NOW.minusSeconds(1800));
        given(readUseCase.findById(RESERVATION_ID)).willReturn(new AdminReservationDetail(
                item.id(), item.reference(), item.restaurantId(), item.restaurantName(), item.restaurantSlug(),
                item.restaurantTimezone(), item.customerName(), item.customerEmail(), item.customerPhone(),
                item.startsAt(), item.endsAt(),
                item.partySize(), item.status(), item.visitStatus(), "Window seat", null,
                item.createdAt(), item.updatedAt(), proposal));

        mockMvc.perform(get("/api/admin/reservations/{id}", RESERVATION_ID)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantTimezone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.alternativeProposal.initiator").value("CUSTOMER"));
    }

    @Test
    void unknownDetailReturnsNotFound() throws Exception {
        given(readUseCase.findById(RESERVATION_ID))
                .willThrow(new AdminResourceNotFoundException("Reservation not found"));

        mockMvc.perform(get("/api/admin/reservations/{id}", RESERVATION_ID)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    private AdminReservation item() {
        return new AdminReservation(
                RESERVATION_ID, "SHK-0001", RESTAURANT_ID, "The Royal Pavilion", "royal-pavilion",
                "Asia/Ho_Chi_Minh", "Minh Lam", "minh@example.com", "0900000000",
                NOW, NOW.plusSeconds(5400), 2,
                "CONFIRMED", "EXPECTED", NOW.minusSeconds(3600), NOW.minusSeconds(3600));
    }
}
