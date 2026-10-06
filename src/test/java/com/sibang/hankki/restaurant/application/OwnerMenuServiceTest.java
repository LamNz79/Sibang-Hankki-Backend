package com.sibang.hankki.restaurant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.model.OwnerMenu;
import com.sibang.hankki.restaurant.application.model.OwnerMenuItemUpdate;
import com.sibang.hankki.restaurant.application.port.out.OwnerMenuPort;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OwnerMenuServiceTest {

    private final OwnerMenuPort port = mock(OwnerMenuPort.class);
    private final OwnerMenuService service = new OwnerMenuService(port);

    @Test
    void normalizesAndCreatesRestaurantScopedItem() {
        UUID restaurantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        OwnerMenu.Item saved = new OwnerMenu.Item(
                UUID.randomUUID(), categoryId, "Pho", null, new BigDecimal("80000.00"), "VND", null, true);
        when(port.createItem(any(), any(), any())).thenReturn(Optional.of(saved));

        OwnerMenu.Item result = service.createItem(restaurantId, actorId, new OwnerMenuItemUpdate(
                categoryId, "  Pho  ", " ", new BigDecimal("80000"), "vnd", " ", true));

        assertEquals(saved, result);
        verify(port).createItem(restaurantId, actorId, new OwnerMenuItemUpdate(
                categoryId, "Pho", null, new BigDecimal("80000.00"), "VND", null, true));
    }

    @Test
    void rejectsInvalidMoneyBeforePersistence() {
        assertThrows(InvalidBookingRequestException.class, () -> service.createItem(
                UUID.randomUUID(), UUID.randomUUID(), new OwnerMenuItemUpdate(
                        UUID.randomUUID(), "Pho", null, new BigDecimal("1.001"), "VND", null, true)));
    }
}
