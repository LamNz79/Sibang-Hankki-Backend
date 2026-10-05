package com.sibang.hankki.user.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantCatalogRepository;
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
class BootstrapOwnerRunnerTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String USERID = "owner";

    @Mock
    private UserAuthenticationRepository userRepository;

    @Mock
    private RestaurantCatalogRepository restaurantRepository;

    private BCryptPasswordEncoder passwordEncoder;
    private BootstrapOwnerRunner runner;
    private String password;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        runner = new BootstrapOwnerRunner(userRepository, restaurantRepository, passwordEncoder);
        password = UUID.randomUUID().toString();
    }

    @Test
    void disabledBootstrapCreatesNothing() {
        runner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void enabledBootstrapCreatesActiveOwnerWithBcryptPassword() {
        enableWithValidConfiguration();
        given(restaurantRepository.existsById(RESTAURANT_ID)).willReturn(true);
        given(userRepository.findByUserid(USERID)).willReturn(Optional.empty());

        runner.run(new DefaultApplicationArguments());

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity created = captor.getValue();
        assertThat(created.getUserid()).isEqualTo(USERID);
        assertThat(created.getRole()).isEqualTo("OWNER");
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        assertThat(created.getRestaurantId()).isEqualTo(RESTAURANT_ID);
        assertThat(passwordEncoder.matches(password, created.getPasswordHash())).isTrue();
        assertThat(created.getPasswordHash()).isNotEqualTo(password);
    }

    @Test
    void matchingExistingOwnerIsUnchanged() {
        enableWithValidConfiguration();
        UserEntity existing = new UserEntity(USERID, "existing-hash", "Existing Owner", "OWNER", RESTAURANT_ID);
        given(restaurantRepository.existsById(RESTAURANT_ID)).willReturn(true);
        given(userRepository.findByUserid(USERID)).willReturn(Optional.of(existing));

        runner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(existing.getPasswordHash()).isEqualTo("existing-hash");
    }

    @Test
    void conflictingExistingUseridFails() {
        enableWithValidConfiguration();
        UserEntity existing = new UserEntity(USERID, "existing-hash", "Customer", "CUSTOMER", null);
        given(restaurantRepository.existsById(RESTAURANT_ID)).willReturn(true);
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
                .hasMessageContaining("APP_BOOTSTRAP_OWNER_USERID is required");
    }

    private void enableWithValidConfiguration() {
        runner.setEnabled(true);
        runner.setUserid(USERID);
        runner.setPassword(password);
        runner.setRestaurantId(RESTAURANT_ID.toString());
    }
}
