package com.sibang.hankki.restaurant.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.port.in.OwnerSlotRegenerationUseCase;
import com.sibang.hankki.restaurant.application.port.out.OwnerBusinessHoursPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantBusinessHourData;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnerBusinessHoursServiceTest {

    private static final UUID RESTAURANT_ID = UUID.randomUUID();

    @Mock
    private OwnerBusinessHoursPort port;
    @Mock
    private OwnerSlotRegenerationUseCase regeneration;

    @Test
    void sortsHoursReplacesThemAndRegeneratesSlots() {
        OwnerBusinessHoursService service = new OwnerBusinessHoursService(port, regeneration);
        given(regeneration.regenerate(RESTAURANT_ID))
                .willReturn(new OwnerSlotRegenerationUseCase.Result(4, 8));
        List<RestaurantBusinessHourData> hours = List.of(
                hour(2, "17:00", "22:00"), hour(1, "11:30", "14:00"));

        service.update(RESTAURANT_ID, hours);

        verify(port).replace(RESTAURANT_ID, List.of(
                hour(1, "11:30", "14:00"), hour(2, "17:00", "22:00")));
        verify(regeneration).regenerate(RESTAURANT_ID);
    }

    @Test
    void rejectsOverlappingPeriodsWithoutWriting() {
        OwnerBusinessHoursService service = new OwnerBusinessHoursService(port, regeneration);

        assertThatThrownBy(() -> service.update(RESTAURANT_ID, List.of(
                hour(1, "11:00", "15:00"), hour(1, "14:00", "18:00"))))
                .isInstanceOf(InvalidBookingRequestException.class);

        verify(port, never()).replace(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private RestaurantBusinessHourData hour(int day, String opensAt, String closesAt) {
        return new RestaurantBusinessHourData(
                RESTAURANT_ID, (short) day, LocalTime.parse(opensAt), LocalTime.parse(closesAt));
    }
}
