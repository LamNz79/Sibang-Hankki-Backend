package com.sibang.hankki.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.exception.InvalidAdminQueryException;
import com.sibang.hankki.admin.application.exception.InvalidAdminStatusTransitionException;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.AdminRestaurantData;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.AdminUserData;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.RestaurantStatus;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.UserStatus;
import com.sibang.hankki.admin.application.port.out.SystemAdminStatusPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemAdminStatusServiceTest {

    private static final UUID USER_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-13T04:00:00Z");

    @Mock
    private SystemAdminStatusPort statusPort;

    private SystemAdminStatusService service;

    @BeforeEach
    void setUp() {
        service = new SystemAdminStatusService(statusPort, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void changesUserBetweenActiveAndSuspendedAndUpdatesTimestamp() {
        var active = user("CUSTOMER", "ACTIVE", NOW.minusSeconds(60));
        var suspended = user("CUSTOMER", "SUSPENDED", NOW);
        given(statusPort.findUserByIdForUpdate(USER_ID)).willReturn(Optional.of(active));
        given(statusPort.updateUserStatus(USER_ID, UserStatus.SUSPENDED, NOW)).willReturn(suspended);

        var result = service.updateUserStatus(USER_ID, "SUSPENDED");

        assertThat(result.status()).isEqualTo("SUSPENDED");
        assertThat(result.statusChangedAt()).isEqualTo(NOW);
        verify(statusPort).updateUserStatus(USER_ID, UserStatus.SUSPENDED, NOW);
    }

    @Test
    void reactivatesSuspendedUser() {
        var suspended = user("CUSTOMER", "SUSPENDED", NOW.minusSeconds(60));
        var active = user("CUSTOMER", "ACTIVE", NOW);
        given(statusPort.findUserByIdForUpdate(USER_ID)).willReturn(Optional.of(suspended));
        given(statusPort.updateUserStatus(USER_ID, UserStatus.ACTIVE, NOW)).willReturn(active);

        var result = service.updateUserStatus(USER_ID, "ACTIVE");

        assertThat(result.status()).isEqualTo("ACTIVE");
        verify(statusPort).updateUserStatus(USER_ID, UserStatus.ACTIVE, NOW);
    }

    @Test
    void sameUserStatusIsIdempotent() {
        var suspended = user("CUSTOMER", "SUSPENDED", NOW.minusSeconds(60));
        given(statusPort.findUserByIdForUpdate(USER_ID)).willReturn(Optional.of(suspended));

        var result = service.updateUserStatus(USER_ID, "SUSPENDED");

        assertThat(result.statusChangedAt()).isEqualTo(NOW.minusSeconds(60));
        verify(statusPort, never()).updateUserStatus(USER_ID, UserStatus.SUSPENDED, NOW);
    }

    @Test
    void rejectsAdminClosedMissingAndInvalidUserStatus() {
        given(statusPort.findUserByIdForUpdate(USER_ID))
                .willReturn(Optional.of(user("ADMIN", "ACTIVE", NOW)));
        assertThatThrownBy(() -> service.updateUserStatus(USER_ID, "SUSPENDED"))
                .isInstanceOf(InvalidAdminStatusTransitionException.class);

        given(statusPort.findUserByIdForUpdate(USER_ID))
                .willReturn(Optional.of(user("CUSTOMER", "CLOSED", NOW)));
        assertThatThrownBy(() -> service.updateUserStatus(USER_ID, "ACTIVE"))
                .isInstanceOf(InvalidAdminStatusTransitionException.class);

        given(statusPort.findUserByIdForUpdate(USER_ID)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateUserStatus(USER_ID, "ACTIVE"))
                .isInstanceOf(AdminResourceNotFoundException.class);
        assertThatThrownBy(() -> service.updateUserStatus(USER_ID, "CLOSED"))
                .isInstanceOf(InvalidAdminQueryException.class);
    }

    @ParameterizedTest
    @MethodSource("allowedRestaurantTransitions")
    void allowsConfiguredRestaurantTransitions(RestaurantStatus current, RestaurantStatus target) {
        given(statusPort.findRestaurantByIdForUpdate(RESTAURANT_ID))
                .willReturn(Optional.of(restaurant(current)));
        given(statusPort.updateRestaurantStatus(RESTAURANT_ID, target))
                .willReturn(restaurant(target));

        var result = service.updateRestaurantStatus(RESTAURANT_ID, target.name());

        assertThat(result.approvalStatus()).isEqualTo(target.name());
        verify(statusPort).updateRestaurantStatus(RESTAURANT_ID, target);
    }

    @Test
    void sameRestaurantStatusIsIdempotent() {
        given(statusPort.findRestaurantByIdForUpdate(RESTAURANT_ID))
                .willReturn(Optional.of(restaurant(RestaurantStatus.CHANGES_REQUESTED)));

        var result = service.updateRestaurantStatus(RESTAURANT_ID, "CHANGES_REQUESTED");

        assertThat(result.approvalStatus()).isEqualTo("CHANGES_REQUESTED");
        verify(statusPort, never()).updateRestaurantStatus(RESTAURANT_ID, RestaurantStatus.CHANGES_REQUESTED);
    }

    @ParameterizedTest
    @MethodSource("rejectedRestaurantTransitions")
    void rejectsUnsupportedRestaurantTransitions(RestaurantStatus current, RestaurantStatus target) {
        given(statusPort.findRestaurantByIdForUpdate(RESTAURANT_ID))
                .willReturn(Optional.of(restaurant(current)));

        assertThatThrownBy(() -> service.updateRestaurantStatus(RESTAURANT_ID, target.name()))
                .isInstanceOf(InvalidAdminStatusTransitionException.class);
        verify(statusPort, never()).updateRestaurantStatus(RESTAURANT_ID, target);
    }

    @Test
    void rejectsMissingRestaurantAndInvalidOrMissingStatus() {
        given(statusPort.findRestaurantByIdForUpdate(RESTAURANT_ID)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateRestaurantStatus(RESTAURANT_ID, "PENDING"))
                .isInstanceOf(AdminResourceNotFoundException.class);
        assertThatThrownBy(() -> service.updateRestaurantStatus(RESTAURANT_ID, "UNKNOWN"))
                .isInstanceOf(InvalidAdminQueryException.class);
        assertThatThrownBy(() -> service.updateRestaurantStatus(RESTAURANT_ID, null))
                .isInstanceOf(InvalidAdminQueryException.class);
    }

    private static Stream<Arguments> allowedRestaurantTransitions() {
        return Stream.of(
                Arguments.of(RestaurantStatus.DRAFT, RestaurantStatus.PENDING),
                Arguments.of(RestaurantStatus.CHANGES_REQUESTED, RestaurantStatus.PENDING),
                Arguments.of(RestaurantStatus.PENDING, RestaurantStatus.ACTIVE),
                Arguments.of(RestaurantStatus.PENDING, RestaurantStatus.CHANGES_REQUESTED),
                Arguments.of(RestaurantStatus.ACTIVE, RestaurantStatus.SUSPENDED),
                Arguments.of(RestaurantStatus.SUSPENDED, RestaurantStatus.ACTIVE));
    }

    private static Stream<Arguments> rejectedRestaurantTransitions() {
        return Stream.of(
                Arguments.of(RestaurantStatus.DRAFT, RestaurantStatus.ACTIVE),
                Arguments.of(RestaurantStatus.DRAFT, RestaurantStatus.SUSPENDED),
                Arguments.of(RestaurantStatus.CHANGES_REQUESTED, RestaurantStatus.ACTIVE),
                Arguments.of(RestaurantStatus.CHANGES_REQUESTED, RestaurantStatus.SUSPENDED),
                Arguments.of(RestaurantStatus.PENDING, RestaurantStatus.SUSPENDED),
                Arguments.of(RestaurantStatus.ACTIVE, RestaurantStatus.PENDING),
                Arguments.of(RestaurantStatus.SUSPENDED, RestaurantStatus.PENDING));
    }

    private AdminUserData user(String role, String status, Instant statusChangedAt) {
        return new AdminUserData(
                USER_ID, "user", "user@example.com", "User", null, role, status, null,
                statusChangedAt, NOW.minusSeconds(120), NOW.minusSeconds(60));
    }

    private AdminRestaurantData restaurant(RestaurantStatus status) {
        return new AdminRestaurantData(
                RESTAURANT_ID, "restaurant", "Restaurant", "ho-chi-minh", "Area", "District",
                status.name(), NOW.minusSeconds(120), NOW.minusSeconds(60));
    }
}
