package com.sibang.hankki.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import com.sibang.hankki.user.adapter.out.persistence.repository.UserAuthenticationRepository;
import com.sibang.hankki.user.application.exception.DuplicateUseridException;
import com.sibang.hankki.user.application.exception.InvalidCustomerRegistrationException;
import com.sibang.hankki.user.application.port.in.RegisterCustomerUseCase.RegisterCustomerCommand;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class CustomerRegistrationServiceTest {

    @Mock
    private UserAuthenticationRepository repository;

    private BCryptPasswordEncoder passwordEncoder;
    private CustomerRegistrationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        passwordEncoder = new BCryptPasswordEncoder();
        service = new CustomerRegistrationService(repository, passwordEncoder);
    }

    @Test
    void registersTrimmedActiveCustomerWithBcryptPasswordAndNoRestaurant() {
        given(repository.saveAndFlush(org.mockito.ArgumentMatchers.any(UserEntity.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        var registered = service.register(new RegisterCustomerCommand(
                "  customer01  ", "  customer@example.com  ", "  Customer Name  ", "  Customer@2026  "));

        ArgumentCaptor<UserEntity> entity = ArgumentCaptor.forClass(UserEntity.class);
        verify(repository).saveAndFlush(entity.capture());
        UserEntity saved = entity.getValue();
        assertEquals("customer01", saved.getUserid());
        assertEquals("customer@example.com", saved.getEmail());
        assertEquals("Customer Name", saved.getName());
        assertEquals("CUSTOMER", saved.getRole());
        assertEquals("ACTIVE", saved.getStatus());
        assertNull(saved.getRestaurantId());
        assertTrue(passwordEncoder.matches("Customer@2026", saved.getPasswordHash()));
        assertEquals("CUSTOMER", registered.role());
    }

    @Test
    void duplicateUseridIsRejectedBeforeSaving() {
        given(repository.existsByUserid("customer01")).willReturn(true);

        assertThrows(DuplicateUseridException.class, () -> service.register(validCommand()));

        verify(repository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest
    @MethodSource("invalidCommands")
    void invalidInputIsRejected(RegisterCustomerCommand command) {
        assertThrows(InvalidCustomerRegistrationException.class, () -> service.register(command));
        verify(repository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    private static java.util.stream.Stream<Arguments> invalidCommands() {
        return java.util.stream.Stream.of(
                Arguments.of((RegisterCustomerCommand) null),
                Arguments.of(new RegisterCustomerCommand("ab", "customer@example.com", "Name", "Customer@2026")),
                Arguments.of(new RegisterCustomerCommand("customer01", "invalid", "Name", "Customer@2026")),
                Arguments.of(new RegisterCustomerCommand("customer01", "customer@example.com", " ", "Customer@2026")),
                Arguments.of(new RegisterCustomerCommand("customer01", "customer@example.com", "Name", "short")));
    }

    private RegisterCustomerCommand validCommand() {
        return new RegisterCustomerCommand(
                "customer01", "customer@example.com", "Customer Name", "Customer@2026");
    }
}
