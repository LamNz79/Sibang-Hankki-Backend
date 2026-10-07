package com.sibang.hankki.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import com.sibang.hankki.user.adapter.out.persistence.repository.UserAuthenticationRepository;
import com.sibang.hankki.user.application.exception.CustomerProfileNotFoundException;
import com.sibang.hankki.user.application.exception.InvalidCustomerProfileException;
import com.sibang.hankki.user.application.port.in.CustomerProfileUseCase.UpdateCustomerProfileCommand;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class CustomerProfileServiceTest {

    private static final UUID USER_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");

    @Mock
    private UserAuthenticationRepository repository;

    private CustomerProfileService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CustomerProfileService(repository);
    }

    @Test
    void readsOnlyTheAuthenticatedCustomer() {
        given(repository.findByIdAndRoleAndStatusAndDeletedAtIsNull(USER_ID, "CUSTOMER", "ACTIVE"))
                .willReturn(Optional.of(customer()));

        var profile = service.get(USER_ID);

        assertEquals("customer", profile.userid());
        verify(repository).findByIdAndRoleAndStatusAndDeletedAtIsNull(USER_ID, "CUSTOMER", "ACTIVE");
    }

    @Test
    void trimsAllowedFieldsAndKeepsUseridImmutable() {
        UserEntity customer = customer();
        given(repository.findByIdAndRoleAndStatusAndDeletedAtIsNull(USER_ID, "CUSTOMER", "ACTIVE"))
                .willReturn(Optional.of(customer));

        var profile = service.update(USER_ID, new UpdateCustomerProfileCommand(
                "  New Name  ", "  new@example.com  ", "   "));

        assertEquals("customer", profile.userid());
        assertEquals("New Name", profile.name());
        assertEquals("new@example.com", profile.email());
        assertNull(profile.phone());
    }

    @Test
    void missingOrDeletedCustomerReturnsNotFound() {
        given(repository.findByIdAndRoleAndStatusAndDeletedAtIsNull(USER_ID, "CUSTOMER", "ACTIVE"))
                .willReturn(Optional.empty());

        assertThrows(CustomerProfileNotFoundException.class, () -> service.get(USER_ID));
    }

    @ParameterizedTest
    @MethodSource("invalidUpdates")
    void rejectsInvalidUpdates(UpdateCustomerProfileCommand command) {
        assertThrows(InvalidCustomerProfileException.class, () -> service.update(USER_ID, command));
    }

    private static java.util.stream.Stream<Arguments> invalidUpdates() {
        return java.util.stream.Stream.of(
                Arguments.of((UpdateCustomerProfileCommand) null),
                Arguments.of(new UpdateCustomerProfileCommand(" ", "customer@example.com", null)),
                Arguments.of(new UpdateCustomerProfileCommand("n".repeat(121), "customer@example.com", null)),
                Arguments.of(new UpdateCustomerProfileCommand("Name", "invalid", null)),
                Arguments.of(new UpdateCustomerProfileCommand("Name", "a".repeat(310) + "@example.com", null)),
                Arguments.of(new UpdateCustomerProfileCommand("Name", "customer@example.com", "1".repeat(31))));
    }

    private UserEntity customer() {
        return new UserEntity("customer", "customer@example.com", "hash", "Customer Name");
    }
}
