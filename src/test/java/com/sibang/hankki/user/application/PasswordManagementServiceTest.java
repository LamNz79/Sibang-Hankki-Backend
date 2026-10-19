package com.sibang.hankki.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import com.sibang.hankki.user.adapter.out.persistence.repository.UserAuthenticationRepository;
import com.sibang.hankki.user.application.exception.IncorrectCurrentPasswordException;
import com.sibang.hankki.user.application.exception.InvalidPasswordRequestException;
import com.sibang.hankki.user.application.port.in.ChangePasswordUseCase.ChangePasswordCommand;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordManagementServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private UserAuthenticationRepository repository;
    @Mock private PasswordEncoder passwordEncoder;

    @Test
    void changesPasswordWithoutTrimmingIt() {
        UserEntity user = new UserEntity("customer", "old-hash", "Customer", "CUSTOMER", null);
        given(repository.findByIdAndStatusAndDeletedAtIsNull(USER_ID, "ACTIVE"))
                .willReturn(Optional.of(user));
        given(passwordEncoder.matches(" OldPassword@2026! ", "old-hash")).willReturn(true);
        given(passwordEncoder.encode(" NewPassword@2026! ")).willReturn("new-hash");

        new PasswordManagementService(repository, passwordEncoder).change(
                USER_ID, new ChangePasswordCommand(" OldPassword@2026! ", " NewPassword@2026! "));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
    }

    @Test
    void wrongCurrentPasswordLeavesHashUnchanged() {
        UserEntity user = new UserEntity("customer", "old-hash", "Customer", "CUSTOMER", null);
        given(repository.findByIdAndStatusAndDeletedAtIsNull(USER_ID, "ACTIVE"))
                .willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong-password", "old-hash")).willReturn(false);

        assertThatThrownBy(() -> new PasswordManagementService(repository, passwordEncoder).change(
                USER_ID, new ChangePasswordCommand("wrong-password", "NewPassword@2026!")))
                .isInstanceOf(IncorrectCurrentPasswordException.class);

        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsMissingFieldsAndInvalidNewPasswordLength() {
        PasswordManagementService service = new PasswordManagementService(repository, passwordEncoder);

        assertThatThrownBy(() -> service.change(USER_ID, null))
                .isInstanceOf(InvalidPasswordRequestException.class);
        assertThatThrownBy(() -> service.change(
                USER_ID, new ChangePasswordCommand("", "NewPassword@2026!")))
                .isInstanceOf(InvalidPasswordRequestException.class);
        assertThatThrownBy(() -> service.change(
                USER_ID, new ChangePasswordCommand("OldPassword@2026!", "short")))
                .isInstanceOf(InvalidPasswordRequestException.class);
        assertThatThrownBy(() -> service.change(
                USER_ID, new ChangePasswordCommand("OldPassword@2026!", "x".repeat(73))))
                .isInstanceOf(InvalidPasswordRequestException.class);
    }
}
