package com.sibang.hankki.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.admin.application.exception.AdminUserConflictException;
import com.sibang.hankki.admin.application.port.in.SystemAdminUserUseCase.CreateUserCommand;
import com.sibang.hankki.admin.application.port.in.SystemAdminUserUseCase.UpdateUserCommand;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.AdminUserData;
import com.sibang.hankki.admin.application.port.out.SystemAdminUserPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class SystemAdminUserServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-14T03:00:00Z");

    @Mock
    private SystemAdminUserPort port;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createsTrimmedCustomerWithEncodedPassword() {
        given(passwordEncoder.encode("CustomerTest@2026!")).willReturn("bcrypt-hash");
        given(port.create("customer01", "customer@example.com", "bcrypt-hash", "Customer", "0901"))
                .willReturn(user("CUSTOMER", null));
        var service = new SystemAdminUserService(port, passwordEncoder);

        var result = service.create(new CreateUserCommand(
                " customer01 ", " customer@example.com ", "CustomerTest@2026!", " Customer ", " 0901 "));

        assertThat(result.role()).isEqualTo("CUSTOMER");
        assertThat(result.restaurantId()).isNull();
        verify(passwordEncoder).encode("CustomerTest@2026!");
    }

    @Test
    void duplicateUseridIsRejectedBeforeEncoding() {
        given(port.existsByUserid("customer01")).willReturn(true);
        var service = new SystemAdminUserService(port, passwordEncoder);

        assertThatThrownBy(() -> service.create(new CreateUserCommand(
                "customer01", null, "CustomerTest@2026!", "Customer", null)))
                .isInstanceOf(AdminUserConflictException.class);
    }

    @Test
    void adminProfileCannotBeUpdated() {
        given(port.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user("ADMIN", null)));
        var service = new SystemAdminUserService(port, passwordEncoder);

        assertThatThrownBy(() -> service.update(
                USER_ID, new UpdateUserCommand("admin@example.com", "Admin", null)))
                .isInstanceOf(AdminUserConflictException.class);
    }

    private AdminUserData user(String role, UUID restaurantId) {
        return new AdminUserData(
                USER_ID, "customer01", "customer@example.com", "Customer", "0901", role, "ACTIVE",
                restaurantId, null, NOW, NOW, NOW);
    }
}
