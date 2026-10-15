package com.sibang.hankki.admin.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.port.out.SystemAdminRestaurantPort;
import com.sibang.hankki.admin.application.port.out.SystemAdminRestaurantPort.RestaurantProfileData;
import com.sibang.hankki.restaurant.application.model.OwnerMenuItemUpdate;
import com.sibang.hankki.restaurant.application.port.in.OwnerMenuUseCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SystemAdminMenuServiceTest {

    private final SystemAdminRestaurantPort restaurantPort =
            org.mockito.Mockito.mock(SystemAdminRestaurantPort.class);
    private final OwnerMenuUseCase menuUseCase = org.mockito.Mockito.mock(OwnerMenuUseCase.class);
    private final SystemAdminMenuService service = new SystemAdminMenuService(restaurantPort, menuUseCase);

    @Test
    void delegatesSelectedRestaurantAndAdminActorToExistingMenuUseCase() {
        UUID restaurantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        OwnerMenuItemUpdate item = new OwnerMenuItemUpdate(
                categoryId, "Beef", null, new BigDecimal("250000"), "VND", null, true);
        given(restaurantPort.findById(restaurantId)).willReturn(Optional.of(restaurant(restaurantId, "DRAFT")));

        service.get(restaurantId);
        service.createCategory(restaurantId, actorId, "Main dishes", 1);
        service.updateCategory(restaurantId, actorId, categoryId, "Main courses", 2);
        service.deleteCategory(restaurantId, actorId, categoryId);
        service.createItem(restaurantId, actorId, item);
        service.updateItem(restaurantId, actorId, itemId, item);
        service.deleteItem(restaurantId, actorId, itemId);

        verify(menuUseCase).get(restaurantId);
        verify(menuUseCase).createCategory(restaurantId, actorId, "Main dishes", 1);
        verify(menuUseCase).updateCategory(restaurantId, actorId, categoryId, "Main courses", 2);
        verify(menuUseCase).deleteCategory(restaurantId, actorId, categoryId);
        verify(menuUseCase).createItem(restaurantId, actorId, item);
        verify(menuUseCase).updateItem(restaurantId, actorId, itemId, item);
        verify(menuUseCase).deleteItem(restaurantId, actorId, itemId);
    }

    @Test
    void rejectsMissingOrArchivedRestaurantBeforeMenuAccess() {
        UUID restaurantId = UUID.randomUUID();
        given(restaurantPort.findById(restaurantId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(restaurantId))
                .isInstanceOf(AdminResourceNotFoundException.class);
        verify(menuUseCase, never()).get(restaurantId);
    }

    private RestaurantProfileData restaurant(UUID id, String status) {
        return new RestaurantProfileData(
                id, "restaurant", "Restaurant", null, null, null, "ho-chi-minh-city",
                null, null, null, null, null, "Asia/Ho_Chi_Minh", null, status,
                Instant.EPOCH, Instant.EPOCH);
    }
}
