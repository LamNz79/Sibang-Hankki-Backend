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
class BootstrapCustomerRunnerTest {

    private static final String USERID = "customer";
    private static final String NAME = "MVP Test Customer";
    private static final String EMAIL = "customer@example.com";

    @Mock
    private UserAuthenticationRepository userRepository;

    private BCryptPasswordEncoder passwordEncoder;
    private BootstrapCustomerRunner runner;
    private String password;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        runner = new BootstrapCustomerRunner(userRepository, passwordEncoder);
        password = UUID.randomUUID().toString();
    }

    @Test
    void disabledBootstrapCreatesNothing() {
        runner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).findByUserid(org.mockito.ArgumentMatchers.anyString());
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void enabledBootstrapCreatesActiveCustomerWithoutRestaurant() {
        enable();
        given(userRepository.findByUserid(USERID)).willReturn(Optional.empty());

        runner.run(new DefaultApplicationArguments());

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity created = captor.getValue();
        assertThat(created.getUserid()).isEqualTo(USERID);
        assertThat(created.getName()).isEqualTo(NAME);
        assertThat(created.getEmail()).isEqualTo(EMAIL);
        assertThat(created.getRole()).isEqualTo("CUSTOMER");
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        assertThat(created.getRestaurantId()).isNull();
        assertThat(passwordEncoder.matches(password, created.getPasswordHash())).isTrue();
        assertThat(created.getPasswordHash()).isNotEqualTo(password);
    }

    @Test
    void matchingExistingCustomerIsUnchanged() {
        enable();
        UserEntity existing = new UserEntity(USERID, "old@example.com", "existing-hash", "Existing Customer");
        given(userRepository.findByUserid(USERID)).willReturn(Optional.of(existing));

        runner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(existing.getPasswordHash()).isEqualTo("existing-hash");
        assertThat(existing.getName()).isEqualTo("Existing Customer");
        assertThat(existing.getEmail()).isEqualTo("old@example.com");
        assertThat(existing.getRole()).isEqualTo("CUSTOMER");
        assertThat(existing.getStatus()).isEqualTo("ACTIVE");
        assertThat(existing.getRestaurantId()).isNull();
    }

    @Test
    void existingLamtestOwnerIsLeftCompletelyUnchanged() {
        enable();
        UUID restaurantId = UUID.randomUUID();
        UserEntity lamtest = new UserEntity("lamtest", "existing-hash", "Lam Test", "OWNER", restaurantId);
        runner.setUserid("lamtest");
        given(userRepository.findByUserid("lamtest")).willReturn(Optional.of(lamtest));

        runner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(lamtest.getPasswordHash()).isEqualTo("existing-hash");
        assertThat(lamtest.getName()).isEqualTo("Lam Test");
        assertThat(lamtest.getRole()).isEqualTo("OWNER");
        assertThat(lamtest.getStatus()).isEqualTo("ACTIVE");
        assertThat(lamtest.getRestaurantId()).isEqualTo(restaurantId);
    }

    @Test
    void missingUseridFailsWithConfigurationError() {
        runner.setEnabled(true);
        runner.setPassword(password);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_BOOTSTRAP_CUSTOMER_USERID is required");
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingPasswordFailsWithConfigurationError() {
        runner.setEnabled(true);
        runner.setUserid(USERID);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_BOOTSTRAP_CUSTOMER_PASSWORD is required");
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private void enable() {
        runner.setEnabled(true);
        runner.setUserid(USERID);
        runner.setPassword(password);
        runner.setName(NAME);
        runner.setEmail(EMAIL);
    }
}
