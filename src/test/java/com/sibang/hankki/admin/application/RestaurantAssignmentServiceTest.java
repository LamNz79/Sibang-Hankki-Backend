package com.sibang.hankki.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.admin.application.exception.AdminUserConflictException;
import com.sibang.hankki.admin.application.port.in.RestaurantAssignmentUseCase.AssignmentCommand;
import com.sibang.hankki.admin.application.port.out.RestaurantAssignmentPort;
import com.sibang.hankki.admin.application.port.out.RestaurantAssignmentPort.AssignmentData;
import com.sibang.hankki.admin.application.port.out.RestaurantAssignmentPort.RestaurantData;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.AdminUserData;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestaurantAssignmentServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID RESTAURANT_ID = UUID.randomUUID();
    private static final UUID OTHER_RESTAURANT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-14T03:00:00Z");

    @Mock
    private RestaurantAssignmentPort port;

    @Test
    void assignsActiveCustomerAsOwnerToDraftRestaurant() {
        given(port.findUserForUpdate(USER_ID)).willReturn(Optional.of(user("CUSTOMER", "ACTIVE", null)));
        given(port.findRestaurant(RESTAURANT_ID))
                .willReturn(Optional.of(new RestaurantData(RESTAURANT_ID, "draft-place", "Draft Place", "DRAFT")));
        given(port.updateAccess(USER_ID, "OWNER", RESTAURANT_ID)).willReturn(new AssignmentData(
                USER_ID, "owner01", "Owner", "owner@example.com", "ACTIVE", "OWNER", RESTAURANT_ID,
                "draft-place", "Draft Place", "DRAFT"));
        var service = new RestaurantAssignmentService(port);

        var result = service.create(new AssignmentCommand(USER_ID, RESTAURANT_ID, "OWNER"));

        assertThat(result.role()).isEqualTo("OWNER");
        assertThat(result.restaurantStatus()).isEqualTo("DRAFT");
    }

    @Test
    void assignsCustomerAsStaff() {
        given(port.findUserForUpdate(USER_ID)).willReturn(Optional.of(user("CUSTOMER", "ACTIVE", null)));
        given(port.findRestaurant(RESTAURANT_ID))
                .willReturn(Optional.of(new RestaurantData(RESTAURANT_ID, "place", "Place", "ACTIVE")));
        given(port.updateAccess(USER_ID, "STAFF", RESTAURANT_ID)).willReturn(assignment("STAFF", RESTAURANT_ID));
        var service = new RestaurantAssignmentService(port);

        assertThat(service.create(new AssignmentCommand(USER_ID, RESTAURANT_ID, "STAFF")).role())
                .isEqualTo("STAFF");
    }

    @Test
    void suspendedAdminAndAlreadyAssignedUsersAreRejected() {
        var service = new RestaurantAssignmentService(port);
        for (AdminUserData user : new AdminUserData[] {
                user("CUSTOMER", "SUSPENDED", null),
                user("ADMIN", "ACTIVE", null),
                user("OWNER", "ACTIVE", RESTAURANT_ID)
        }) {
            given(port.findUserForUpdate(USER_ID)).willReturn(Optional.of(user));
            assertThatThrownBy(() -> service.create(new AssignmentCommand(USER_ID, RESTAURANT_ID, "OWNER")))
                    .isInstanceOf(AdminUserConflictException.class);
        }
    }

    @Test
    void ownerCanBeReassignedAndChangedToStaff() {
        given(port.findUserForUpdate(USER_ID)).willReturn(Optional.of(user("OWNER", "ACTIVE", RESTAURANT_ID)));
        given(port.findRestaurant(OTHER_RESTAURANT_ID)).willReturn(Optional.of(
                new RestaurantData(OTHER_RESTAURANT_ID, "other", "Other", "SUSPENDED")));
        given(port.updateAccess(USER_ID, "STAFF", OTHER_RESTAURANT_ID))
                .willReturn(assignment("STAFF", OTHER_RESTAURANT_ID));
        var service = new RestaurantAssignmentService(port);

        var result = service.update(
                USER_ID, new AssignmentCommand(null, OTHER_RESTAURANT_ID, "STAFF"));

        assertThat(result.role()).isEqualTo("STAFF");
        assertThat(result.restaurantId()).isEqualTo(OTHER_RESTAURANT_ID);
    }

    @Test
    void removalConvertsSuspendedOwnerToCustomerWithoutRestaurant() {
        given(port.findUserForUpdate(USER_ID))
                .willReturn(Optional.of(user("OWNER", "SUSPENDED", RESTAURANT_ID)));
        given(port.updateAccess(USER_ID, "CUSTOMER", null)).willReturn(assignment("CUSTOMER", null));
        var service = new RestaurantAssignmentService(port);

        service.remove(USER_ID);

        verify(port).updateAccess(USER_ID, "CUSTOMER", null);
    }

    private AdminUserData user(String role, String status, UUID restaurantId) {
        return new AdminUserData(
                USER_ID, "owner01", "owner@example.com", "Owner", null, role, status,
                restaurantId, null, NOW, NOW, NOW);
    }

    private AssignmentData assignment(String role, UUID restaurantId) {
        return new AssignmentData(
                USER_ID, "owner01", "Owner", "owner@example.com", "ACTIVE", role, restaurantId,
                restaurantId == null ? null : "place", restaurantId == null ? null : "Place",
                restaurantId == null ? null : "ACTIVE");
    }
}
