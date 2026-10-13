package com.sibang.hankki.user.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import com.sibang.hankki.user.adapter.out.persistence.repository.UserAuthenticationRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminRunnerTest {

    private static final String USERID = "system-admin";

    @Mock
    private UserAuthenticationRepository userRepository;

    private BCryptPasswordEncoder passwordEncoder;
    private BootstrapAdminRunner runner;
    private String password;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        runner = new BootstrapAdminRunner(userRepository, passwordEncoder);
        password = UUID.randomUUID().toString();
    }

    @Test
    void disabledBootstrapCreatesNothing() {
        runner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void enabledBootstrapCreatesActiveAdminWithoutRestaurant() {
        enable();
        given(userRepository.findByUserid(USERID)).willReturn(Optional.empty());

        runner.run(new DefaultApplicationArguments());

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity created = captor.getValue();
        assertThat(created.getUserid()).isEqualTo(USERID);
        assertThat(created.getRole()).isEqualTo("ADMIN");
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        assertThat(created.getRestaurantId()).isNull();
        assertThat(passwordEncoder.matches(password, created.getPasswordHash())).isTrue();
        assertThat(created.getPasswordHash()).isNotEqualTo(password);
    }

    @Test
    void matchingAdminIsUnchanged() {
        enable();
        UserEntity existing = new UserEntity(USERID, "existing-hash", "Admin", "ADMIN", null);
        given(userRepository.findByUserid(USERID)).willReturn(Optional.of(existing));

        runner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(existing.getPasswordHash()).isEqualTo("existing-hash");
    }

    @Test
    void conflictingUseridFails() {
        enable();
        UserEntity existing = new UserEntity(USERID, "hash", "Owner", "OWNER", UUID.randomUUID());
        given(userRepository.findByUserid(USERID)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("another role or restaurant");
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingConfigurationFails() {
        runner.setEnabled(true);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_BOOTSTRAP_ADMIN_USERID is required");
    }

    @Test
    void invalidUseridLengthFails() {
        runner.setEnabled(true);
        runner.setUserid("ab");
        runner.setPassword(password);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("between 3 and 30");
    }

    private void enable() {
        runner.setEnabled(true);
        runner.setUserid(USERID);
        runner.setPassword(password);
    }
}
