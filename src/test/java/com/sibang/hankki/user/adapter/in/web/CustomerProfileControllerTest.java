package com.sibang.hankki.user.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sibang.hankki.auth.OwnerSessionUserDetailsService;
import com.sibang.hankki.auth.config.SecurityConfig;
import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import com.sibang.hankki.user.adapter.out.persistence.repository.UserAuthenticationRepository;
import com.sibang.hankki.user.application.exception.CustomerProfileNotFoundException;
import com.sibang.hankki.user.application.exception.InvalidCustomerProfileException;
import com.sibang.hankki.user.application.port.in.CustomerProfileUseCase;
import com.sibang.hankki.user.application.port.in.CustomerProfileUseCase.CustomerProfile;
import com.sibang.hankki.user.application.port.in.CustomerProfileUseCase.UpdateCustomerProfileCommand;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(CustomerProfileController.class)
@Import({SecurityConfig.class, OwnerSessionUserDetailsService.class})
class CustomerProfileControllerTest {

    private static final String PASSWORD = "test-password";
    private static final UUID CUSTOMER_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private CustomerProfileUseCase useCase;
    @MockitoBean
    private UserAuthenticationRepository repository;

    @BeforeEach
    void configureUsers() {
        configureUser("customer", "CUSTOMER", CUSTOMER_ID);
        configureUser("owner", "OWNER", UUID.randomUUID());
        configureUser("staff", "STAFF", UUID.randomUUID());
        configureUser("admin", "ADMIN", UUID.randomUUID());
    }

    @Test
    void unauthenticatedRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/customer/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerStaffAndAdminCannotAccessCustomerProfile() throws Exception {
        for (String userid : new String[] {"owner", "staff", "admin"}) {
            mockMvc.perform(get("/api/customer/profile").session(login(userid)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(useCase);
    }

    @Test
    void customerReadsProfileUsingAuthenticatedUserId() throws Exception {
        given(useCase.get(CUSTOMER_ID)).willReturn(profile());

        mockMvc.perform(get("/api/customer/profile").session(login("customer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.userid").value("customer"))
                .andExpect(jsonPath("$.name").value("Customer Name"))
                .andExpect(jsonPath("$.email").value("customer@example.com"))
                .andExpect(jsonPath("$.phone").value("0900000000"));

        verify(useCase).get(CUSTOMER_ID);
    }

    @Test
    void updateRequiresCsrf() throws Exception {
        mockMvc.perform(put("/api/customer/profile")
                        .session(login("customer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(useCase);
    }

    @Test
    void updateUsesAuthenticatedIdAndIgnoresPrivilegeFields() throws Exception {
        given(useCase.update(org.mockito.ArgumentMatchers.eq(CUSTOMER_ID), org.mockito.ArgumentMatchers.any()))
                .willReturn(profile());

        mockMvc.perform(put("/api/customer/profile")
                        .with(csrf())
                        .session(login("customer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Customer Name",
                                  "email": "customer@example.com",
                                  "phone": "0900000000",
                                  "id": "80000000-0000-0000-0000-000000000001",
                                  "userid": "admin",
                                  "role": "ADMIN",
                                  "status": "ACTIVE",
                                  "restaurantId": "00000000-0000-0000-0000-000000000001",
                                  "password": "changed"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userid").value("customer"))
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.restaurantId").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        ArgumentCaptor<UpdateCustomerProfileCommand> command =
                ArgumentCaptor.forClass(UpdateCustomerProfileCommand.class);
        verify(useCase).update(org.mockito.ArgumentMatchers.eq(CUSTOMER_ID), command.capture());
        org.junit.jupiter.api.Assertions.assertEquals("Customer Name", command.getValue().name());
        org.junit.jupiter.api.Assertions.assertEquals("customer@example.com", command.getValue().email());
        org.junit.jupiter.api.Assertions.assertEquals("0900000000", command.getValue().phone());
    }

    @Test
    void invalidDataAndMissingProfileMapToExpectedStatuses() throws Exception {
        given(useCase.update(org.mockito.ArgumentMatchers.eq(CUSTOMER_ID), org.mockito.ArgumentMatchers.any()))
                .willThrow(new InvalidCustomerProfileException("invalid"));
        given(useCase.get(CUSTOMER_ID)).willThrow(new CustomerProfileNotFoundException());
        MockHttpSession session = login("customer");

        mockMvc.perform(put("/api/customer/profile")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(get("/api/customer/profile").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private void configureUser(String userid, String role, UUID id) {
        UserEntity user = org.mockito.Mockito.mock(UserEntity.class);
        given(user.getId()).willReturn(id);
        given(user.getUserid()).willReturn(userid);
        given(user.getPasswordHash()).willReturn(passwordEncoder.encode(PASSWORD));
        given(user.getRole()).willReturn(role);
        given(repository.findByUseridAndStatusAndDeletedAtIsNull(userid, "ACTIVE"))
                .willReturn(Optional.of(user));
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

    private CustomerProfile profile() {
        return new CustomerProfile(
                CUSTOMER_ID, "customer", "Customer Name", "customer@example.com", "0900000000");
    }

    private String updateJson() {
        return """
                {
                  "name": "Customer Name",
                  "email": "customer@example.com",
                  "phone": "0900000000"
                }
                """;
    }
}
