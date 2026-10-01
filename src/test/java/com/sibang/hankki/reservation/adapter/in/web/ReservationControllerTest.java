package com.sibang.hankki.reservation.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sibang.hankki.reservation.application.exception.InvalidReservationRequestException;
import com.sibang.hankki.reservation.application.exception.ReservationCapacityUnavailableException;
import com.sibang.hankki.reservation.application.port.in.CreateReservationCommand;
import com.sibang.hankki.reservation.application.port.in.CreateReservationResult;
import com.sibang.hankki.reservation.application.port.in.CreateReservationUseCase;
import com.sibang.hankki.reservation.domain.model.Reservation;
import com.sibang.hankki.reservation.domain.model.ReservationStatus;
import com.sibang.hankki.reservation.domain.model.VisitStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReservationController.class)
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateReservationUseCase createReservationUseCase;

    @Test
    void createsReservationWithThePublicResponseContract() throws Exception {
        given(createReservationUseCase.create(org.mockito.ArgumentMatchers.any(CreateReservationCommand.class)))
                .willReturn(result(false));

        mockMvc.perform(post("/api/reservations")
                        .header("Idempotency-Key", "request-1")
                        .contentType("application/json")
                        .content(requestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("SHK-ABC123"))
                .andExpect(jsonPath("$.restaurantSlug").value("anan-saigon"))
                .andExpect(jsonPath("$.date").value("2026-10-05"))
                .andExpect(jsonPath("$.time").value("18:30"))
                .andExpect(jsonPath("$.partySize").value(2))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.requiresRestaurantConfirmation").value(false));
    }

    @Test
    void idempotencyReplayReturnsOk() throws Exception {
        given(createReservationUseCase.create(org.mockito.ArgumentMatchers.any(CreateReservationCommand.class)))
                .willReturn(result(true));

        mockMvc.perform(post("/api/reservations")
                        .header("Idempotency-Key", "request-1")
                        .contentType("application/json")
                        .content(requestJson()))
                .andExpect(status().isOk());
    }

    @Test
    void mapsValidationAndCapacityErrors() throws Exception {
        given(createReservationUseCase.create(org.mockito.ArgumentMatchers.any(CreateReservationCommand.class)))
                .willThrow(new InvalidReservationRequestException("invalid"));
        mockMvc.perform(post("/api/reservations").contentType("application/json").content(requestJson()))
                .andExpect(status().isBadRequest());

        given(createReservationUseCase.create(org.mockito.ArgumentMatchers.any(CreateReservationCommand.class)))
                .willThrow(new ReservationCapacityUnavailableException());
        mockMvc.perform(post("/api/reservations")
                        .header("Idempotency-Key", "request-1")
                        .contentType("application/json")
                        .content(requestJson()))
                .andExpect(status().isConflict());
    }

    private CreateReservationResult result(boolean replay) {
        Reservation reservation = new Reservation(
                UUID.randomUUID(), "SHK-ABC123", "request-1", "a".repeat(64), UUID.randomUUID(), UUID.randomUUID(),
                null, "Minh Lam", null, "0900000000", Instant.parse("2026-10-05T11:30:00Z"),
                Instant.parse("2026-10-05T13:00:00Z"), 2, ReservationStatus.CONFIRMED, false, VisitStatus.EXPECTED,
                null, null, null, null, null, 0, Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"));
        return new CreateReservationResult(
                reservation, "anan-saigon", LocalDate.of(2026, 10, 5), LocalTime.of(18, 30), false, replay);
    }

    private String requestJson() {
        return """
                {"restaurantSlug":"anan-saigon","date":"2026-10-05","time":"18:30","partySize":2,
                 "customerName":"Minh Lam","customerPhone":"0900000000"}
                """;
    }
}
