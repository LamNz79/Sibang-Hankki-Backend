package com.sibang.hankki.reservation.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sibang.hankki.auth.OwnerSessionUserDetailsService;
import com.sibang.hankki.auth.config.SecurityConfig;
import com.sibang.hankki.reservation.application.exception.ReservationNotFoundException;
import com.sibang.hankki.reservation.application.port.in.CustomerAccountReservationUseCase;
import com.sibang.hankki.reservation.application.port.in.CustomerAccountReservationUseCase.CustomerAccountReservation;
import com.sibang.hankki.reservation.application.port.in.ReservationProposalUseCase;
import com.sibang.hankki.reservation.application.port.in.ReservationProposalUseCase.ProposalResult;
import com.sibang.hankki.reservation.domain.model.Reservation;
import com.sibang.hankki.reservation.domain.model.ReservationProposal;
import com.sibang.hankki.reservation.domain.model.ReservationProposalStatus;
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

@WebMvcTest(CustomerAccountReservationController.class)
@Import({SecurityConfig.class, OwnerSessionUserDetailsService.class})
class CustomerAccountReservationControllerTest {

    private static final String PASSWORD = "test-password";
    private static final UUID CUSTOMER_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private CustomerAccountReservationUseCase useCase;
    @MockitoBean
    private ReservationProposalUseCase proposalUseCase;
    @MockitoBean
    private UserAuthenticationRepository userRepository;

    @BeforeEach
    void configureUsers() {
        configureUser("customer", "CUSTOMER", CUSTOMER_ID);
        configureUser("owner", "OWNER", UUID.randomUUID());
        configureUser("staff", "STAFF", UUID.randomUUID());
        configureUser("admin", "ADMIN", UUID.randomUUID());
    }

    @Test
    void accountEndpointsRequireCustomerRole() throws Exception {
        mockMvc.perform(get("/api/customer/account/reservations"))
                .andExpect(status().isUnauthorized());
        for (String userid : new String[] {"owner", "staff", "admin"}) {
            mockMvc.perform(get("/api/customer/account/reservations").session(login(userid)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(useCase);
    }

    @Test
    void listAndDetailUseAuthenticatedCustomerIdWithoutExposingSecrets() throws Exception {
        CustomerAccountReservation view = view(ReservationStatus.CONFIRMED, VisitStatus.EXPECTED);
        given(useCase.findAccountReservations(CUSTOMER_ID)).willReturn(List.of(view));
        given(useCase.findAccountReservation(RESERVATION_ID, CUSTOMER_ID)).willReturn(view);
        MockHttpSession session = login("customer");

        mockMvc.perform(get("/api/customer/account/reservations").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].restaurantSlug").value("the-royal-pavilion"))
                .andExpect(jsonPath("$[0].restaurantName").value("The Royal Pavilion"))
                .andExpect(jsonPath("$[0].restaurantTimezone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$[0].managementToken").doesNotExist());
        mockMvc.perform(get("/api/customer/account/reservations/{id}", RESERVATION_ID).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RESERVATION_ID.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        verify(useCase).findAccountReservations(CUSTOMER_ID);
        verify(useCase).findAccountReservation(RESERVATION_ID, CUSTOMER_ID);
    }

    @Test
    void crossAccountDetailIsNotFound() throws Exception {
        given(useCase.findAccountReservation(RESERVATION_ID, CUSTOMER_ID))
                .willThrow(new ReservationNotFoundException(RESERVATION_ID));

        mockMvc.perform(get("/api/customer/account/reservations/{id}", RESERVATION_ID)
                        .session(login("customer")))
                .andExpect(status().isNotFound());
    }

    @Test
    void accountCancellationRequiresCsrf() throws Exception {
        MockHttpSession session = login("customer");
        mockMvc.perform(post("/api/customer/account/reservations/{id}/cancel", RESERVATION_ID)
                        .session(session))
                .andExpect(status().isForbidden());

        given(useCase.cancelAccountReservation(RESERVATION_ID, CUSTOMER_ID))
                .willReturn(view(ReservationStatus.CANCELLED, null));
        mockMvc.perform(post("/api/customer/account/reservations/{id}/cancel", RESERVATION_ID)
                        .with(csrf())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void accountCheckInTokenRequiresCustomerAndCsrf() throws Exception {
        MockHttpSession customer = login("customer");
        mockMvc.perform(post("/api/customer/account/reservations/{id}/check-in-token", RESERVATION_ID)
                        .session(customer))
                .andExpect(status().isForbidden());

        given(useCase.issueAccountCheckInToken(RESERVATION_ID, CUSTOMER_ID))
                .willReturn("opaque-check-in-token");
        mockMvc.perform(post("/api/customer/account/reservations/{id}/check-in-token", RESERVATION_ID)
                        .with(csrf())
                        .session(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()))
                .andExpect(jsonPath("$.checkInToken").value("opaque-check-in-token"))
                .andExpect(jsonPath("$.checkInTokenHash").doesNotExist());

        mockMvc.perform(post("/api/customer/account/reservations/{id}/check-in-token", RESERVATION_ID)
                        .with(csrf())
                        .session(login("owner")))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountCustomerAcceptsOwnProposalWithCsrf() throws Exception {
        CustomerAccountReservation confirmed = view(ReservationStatus.CONFIRMED, VisitStatus.EXPECTED);
        ReservationProposal proposal = proposal(ReservationProposalStatus.ACCEPTED);
        given(proposalUseCase.acceptForAccount(RESERVATION_ID, proposal.id(), CUSTOMER_ID))
                .willReturn(new ProposalResult(confirmed.reservation(), proposal));
        given(useCase.findAccountReservation(RESERVATION_ID, CUSTOMER_ID)).willReturn(confirmed);

        mockMvc.perform(post("/api/customer/account/reservations/{id}/proposals/{proposalId}/accept",
                        RESERVATION_ID, proposal.id())
                        .with(csrf())
                        .session(login("customer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.alternativeProposal.status").value("ACCEPTED"));
    }

    @Test
    void accountCustomerRequestsAnotherTimeWithCsrf() throws Exception {
        UUID proposalId = UUID.randomUUID();
        CustomerAccountReservation pending = view(ReservationStatus.PENDING, null);
        given(proposalUseCase.requestAnotherTimeForAccount(
                RESERVATION_ID, proposalId, CUSTOMER_ID, "2026-10-20", "18:30"))
                .willReturn(pending.reservation());
        given(useCase.findAccountReservation(RESERVATION_ID, CUSTOMER_ID)).willReturn(pending);
        MockHttpSession customer = login("customer");
        String endpoint = "/api/customer/account/reservations/{id}/proposals/{proposalId}/request-another-time";
        String body = "{\"requestedDate\":\"2026-10-20\",\"requestedTime\":\"18:30\"}";

        mockMvc.perform(post(endpoint, RESERVATION_ID, proposalId)
                        .session(customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(endpoint, RESERVATION_ID, proposalId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(endpoint, RESERVATION_ID, proposalId)
                        .with(csrf())
                        .session(customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.alternativeProposal").doesNotExist());

        mockMvc.perform(post(endpoint, RESERVATION_ID, proposalId)
                        .with(csrf())
                        .session(login("owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountChangeRequestRequiresCustomerSessionAndCsrf() throws Exception {
        CustomerAccountReservation confirmed = view(ReservationStatus.CONFIRMED, VisitStatus.EXPECTED);
        ReservationProposal proposal = new ReservationProposal(
                UUID.randomUUID(), RESERVATION_ID, UUID.randomUUID(),
                Instant.parse("2026-10-20T11:30:00Z"), 2, 0, "Window",
                null, ReservationProposalStatus.PENDING,
                com.sibang.hankki.reservation.domain.model.ReservationProposalInitiator.CUSTOMER,
                CUSTOMER_ID, Instant.parse("2026-10-01T00:00:00Z"), null, null);
        given(proposalUseCase.requestChangeForAccount(
                RESERVATION_ID, CUSTOMER_ID, "2026-10-20", "18:30", "Window"))
                .willReturn(new ProposalResult(confirmed.reservation(), proposal));
        given(useCase.findAccountReservation(RESERVATION_ID, CUSTOMER_ID)).willReturn(confirmed);
        String body = "{\"requestedDate\":\"2026-10-20\",\"requestedTime\":\"18:30\",\"message\":\"Window\"}";

        mockMvc.perform(post("/api/customer/account/reservations/{id}/change-requests", RESERVATION_ID)
                        .session(login("customer"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/customer/account/reservations/{id}/change-requests", RESERVATION_ID)
                        .with(csrf()).session(login("owner"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/customer/account/reservations/{id}/change-requests", RESERVATION_ID)
                        .with(csrf()).session(login("customer"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alternativeProposal.initiator").value("CUSTOMER"));
    }

    private void configureUser(String userid, String role, UUID id) {
        UserEntity user = org.mockito.Mockito.mock(UserEntity.class);
        given(user.getId()).willReturn(id);
        given(user.getUserid()).willReturn(userid);
        given(user.getPasswordHash()).willReturn(passwordEncoder.encode(PASSWORD));
        given(user.getRole()).willReturn(role);
        given(userRepository.findByUseridAndStatusAndDeletedAtIsNull(userid, "ACTIVE"))
                .willReturn(Optional.of(user));
    }

    private MockHttpSession login(String userid) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("userid", userid)
                        .param("password", PASSWORD))
                .andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);
    }

    private CustomerAccountReservation view(ReservationStatus status, VisitStatus visitStatus) {
        Instant startsAt = Instant.parse("2026-10-10T11:30:00Z");
        Reservation reservation = new Reservation(
                RESERVATION_ID, "SHK-ACCOUNT-1", "account-key", "a".repeat(64), UUID.randomUUID(), UUID.randomUUID(),
                CUSTOMER_ID, "Customer", "customer@example.com", "0900000000", startsAt,
                startsAt.plusSeconds(90 * 60L), 2, status, false, visitStatus, "Window", "No peanuts",
                null, null, null, null, 0, Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-01T00:00:00Z"));
        return new CustomerAccountReservation(
                reservation, "the-royal-pavilion", "The Royal Pavilion", "Asia/Ho_Chi_Minh");
    }

    private ReservationProposal proposal(ReservationProposalStatus status) {
        return new ReservationProposal(
                UUID.randomUUID(), RESERVATION_ID, UUID.randomUUID(),
                Instant.parse("2026-10-14T11:00:00Z"), 2, 1, "Later", null, status,
                UUID.randomUUID(), Instant.parse("2026-10-13T02:00:00Z"), CUSTOMER_ID,
                Instant.parse("2026-10-13T03:00:00Z"));
    }
}
