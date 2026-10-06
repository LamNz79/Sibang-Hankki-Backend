package com.sibang.hankki.reservation.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sibang.hankki.auth.config.SecurityConfig;
import com.sibang.hankki.reservation.application.exception.ReservationNotFoundException;
import com.sibang.hankki.reservation.application.port.in.CustomerReservationUseCase;
import com.sibang.hankki.reservation.domain.model.Reservation;
import com.sibang.hankki.reservation.domain.model.ReservationStatus;
import com.sibang.hankki.reservation.domain.model.VisitStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerReservationController.class)
@Import(SecurityConfig.class)
class CustomerReservationControllerTest {

    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final String TOKEN = "guest-management-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerReservationUseCase useCase;

    @Test
    void readsCurrentStatusWithHeaderToken() throws Exception {
        given(useCase.findById(RESERVATION_ID, TOKEN))
                .willReturn(reservation(ReservationStatus.CONFIRMED, VisitStatus.EXPECTED));

        mockMvc.perform(get("/api/customer/reservations/{id}", RESERVATION_ID)
                        .header(CustomerReservationController.MANAGEMENT_TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.visitStatus").value("EXPECTED"));
    }

    @Test
    void invalidOrMissingTokenReturnsNotFound() throws Exception {
        given(useCase.findById(RESERVATION_ID, null)).willThrow(new ReservationNotFoundException(RESERVATION_ID));

        mockMvc.perform(get("/api/customer/reservations/{id}", RESERVATION_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancellationIsPublicAndCsrfFree() throws Exception {
        given(useCase.cancel(RESERVATION_ID, TOKEN))
                .willReturn(reservation(ReservationStatus.CANCELLED, null));

        mockMvc.perform(post("/api/customer/reservations/{id}/cancel", RESERVATION_ID)
                        .header(CustomerReservationController.MANAGEMENT_TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.visitStatus").doesNotExist());

        verify(useCase).cancel(RESERVATION_ID, TOKEN);
    }

    private Reservation reservation(ReservationStatus status, VisitStatus visitStatus) {
        return new Reservation(
                RESERVATION_ID, "SHK-GUEST-1", "guest-key", "a".repeat(64), UUID.randomUUID(), UUID.randomUUID(),
                null, "Minh Lam", null, "0900000000", Instant.parse("2026-10-10T11:30:00Z"),
                Instant.parse("2026-10-10T13:00:00Z"), 2, status, false, visitStatus,
                null, null, "a".repeat(64), null, null, null, 0,
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"));
    }
}
