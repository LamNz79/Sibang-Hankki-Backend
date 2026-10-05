package com.sibang.hankki.reservation.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sibang.hankki.auth.OwnerSessionUserDetailsService;
import com.sibang.hankki.auth.config.SecurityConfig;
import com.sibang.hankki.reservation.application.exception.ReservationNotFoundException;
import com.sibang.hankki.reservation.application.port.in.OwnerReservationReadUseCase;
import com.sibang.hankki.reservation.domain.model.Reservation;
import com.sibang.hankki.reservation.domain.model.ReservationStatus;
import com.sibang.hankki.reservation.domain.model.VisitStatus;
import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import com.sibang.hankki.user.adapter.out.persistence.repository.UserAuthenticationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(OwnerReservationController.class)
@Import({SecurityConfig.class, OwnerSessionUserDetailsService.class})
class OwnerReservationControllerTest {

    private static final String PASSWORD = "test-password";
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private OwnerReservationReadUseCase readUseCase;

    @MockitoBean
    private UserAuthenticationRepository userRepository;

    @BeforeEach
    void configureUsers() {
        UserEntity owner = user("owner", "OWNER");
        UserEntity staff = user("staff", "STAFF");
        given(userRepository.findByUseridAndStatusAndDeletedAtIsNull("owner", "ACTIVE"))
                .willReturn(Optional.of(owner));
        given(userRepository.findByUseridAndStatusAndDeletedAtIsNull("staff", "ACTIVE"))
                .willReturn(Optional.of(staff));
    }

    @Test
    void unauthenticatedListReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/owner/reservations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerListUsesSessionRestaurantAndHidesInternalFields() throws Exception {
        given(readUseCase.findAll(RESTAURANT_ID)).willReturn(List.of(reservation()));

        mockMvc.perform(get("/api/owner/reservations").session(login("owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(RESERVATION_ID.toString()))
                .andExpect(jsonPath("$[0].reference").value("SHK-OWNER-1"))
                .andExpect(jsonPath("$[0].customerName").value("Minh Lam"))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].visitStatus").value("EXPECTED"))
                .andExpect(jsonPath("$[0].idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$[0].requestFingerprint").doesNotExist())
                .andExpect(jsonPath("$[0].checkInTokenHash").doesNotExist())
                .andExpect(jsonPath("$[0].version").doesNotExist());

        verify(readUseCase).findAll(RESTAURANT_ID);
    }

    @Test
    void staffCanReadOwnedDetail() throws Exception {
        given(readUseCase.findById(RESERVATION_ID, RESTAURANT_ID)).willReturn(reservation());

        mockMvc.perform(get("/api/owner/reservations/{id}", RESERVATION_ID).session(login("staff")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RESERVATION_ID.toString()));

        verify(readUseCase).findById(RESERVATION_ID, RESTAURANT_ID);
    }

    @Test
    void detailFromAnotherRestaurantReturnsNotFound() throws Exception {
        given(readUseCase.findById(RESERVATION_ID, RESTAURANT_ID))
                .willThrow(new ReservationNotFoundException(RESERVATION_ID));

        mockMvc.perform(get("/api/owner/reservations/{id}", RESERVATION_ID).session(login("owner")))
                .andExpect(status().isNotFound());
    }

    private MockHttpSession login(String userid) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("userid", userid)
                        .param("password", PASSWORD))
                .andExpect(status().isNoContent())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private UserEntity user(String userid, String role) {
        UserEntity user = org.mockito.Mockito.mock(UserEntity.class);
        given(user.getId()).willReturn(UUID.randomUUID());
        given(user.getUserid()).willReturn(userid);
        given(user.getPasswordHash()).willReturn(passwordEncoder.encode(PASSWORD));
        given(user.getRole()).willReturn(role);
        given(user.getRestaurantId()).willReturn(RESTAURANT_ID);
        return user;
    }

    private Reservation reservation() {
        return new Reservation(
                RESERVATION_ID,
                "SHK-OWNER-1",
                "secret-key",
                "a".repeat(64),
                RESTAURANT_ID,
                UUID.randomUUID(),
                null,
                "Minh Lam",
                "minh@example.com",
                "0900000000",
                Instant.parse("2026-10-05T11:30:00Z"),
                Instant.parse("2026-10-05T13:00:00Z"),
                2,
                ReservationStatus.CONFIRMED,
                false,
                VisitStatus.EXPECTED,
                "Window seat",
                "No peanuts",
                "secret-check-in-hash",
                null,
                null,
                3,
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-02T00:00:00Z"));
    }
}
