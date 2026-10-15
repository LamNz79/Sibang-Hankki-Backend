package com.sibang.hankki.admin.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.port.out.SystemAdminRestaurantPort;
import com.sibang.hankki.admin.application.port.out.SystemAdminRestaurantPort.RestaurantProfileData;
import com.sibang.hankki.restaurant.application.model.BookingSettingsUpdate;
import com.sibang.hankki.restaurant.application.port.in.OwnerBusinessHoursUseCase;
import com.sibang.hankki.restaurant.application.port.in.OwnerSettingsUseCase;
import com.sibang.hankki.restaurant.application.port.in.OwnerSlotRegenerationUseCase;
import com.sibang.hankki.restaurant.application.port.out.RestaurantBusinessHourData;
import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemAdminBookingConfigurationServiceTest {

    private static final UUID RESTAURANT_A = UUID.randomUUID();
    private static final UUID RESTAURANT_B = UUID.randomUUID();

    @Mock
    private SystemAdminRestaurantPort restaurantPort;
    @Mock
    private OwnerSettingsUseCase settingsUseCase;
    @Mock
    private OwnerBusinessHoursUseCase businessHoursUseCase;
    @Mock
    private OwnerSlotRegenerationUseCase regenerationUseCase;

    private SystemAdminBookingConfigurationService service;

    @BeforeEach
    void setUp() {
        service = new SystemAdminBookingConfigurationService(
                restaurantPort, settingsUseCase, businessHoursUseCase, regenerationUseCase);
        org.mockito.Mockito.lenient().when(restaurantPort.findById(RESTAURANT_A))
                .thenReturn(Optional.of(restaurant(RESTAURANT_A)));
    }

    @Test
    void delegatesSettingsHoursAndRegenerationWithSelectedRestaurantOnly() {
        BookingSettingsUpdate update = update(null, 0);
        List<RestaurantBusinessHourData> hours = List.of(new RestaurantBusinessHourData(
                RESTAURANT_A, (short) 1, LocalTime.of(11, 30), LocalTime.of(14, 0)));

        service.updateBookingSettings(RESTAURANT_A, update);
        service.updateBusinessHours(RESTAURANT_A, hours);
        service.regenerateSlots(RESTAURANT_A);

        verify(settingsUseCase).updateForAdministration(RESTAURANT_A, update);
        verify(businessHoursUseCase).updateForAdministration(
                RESTAURANT_A, hours, ZoneId.of("Asia/Ho_Chi_Minh"));
        verify(regenerationUseCase).regenerateForAdministration(
                RESTAURANT_A, ZoneId.of("Asia/Ho_Chi_Minh"));
        verify(settingsUseCase, never()).updateForAdministration(
                org.mockito.ArgumentMatchers.eq(RESTAURANT_B), org.mockito.ArgumentMatchers.any());
        verify(businessHoursUseCase, never()).updateForAdministration(
                org.mockito.ArgumentMatchers.eq(RESTAURANT_B),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsUnknownRestaurantBeforeCallingRestaurantConfiguration() {
        given(restaurantPort.findById(RESTAURANT_B)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBusinessHours(RESTAURANT_B))
                .isInstanceOf(AdminResourceNotFoundException.class);

        verify(businessHoursUseCase, never()).get(RESTAURANT_B);
    }

    private BookingSettingsUpdate update(Integer cancellationCutoff, Integer noShowGrace) {
        return new BookingSettingsUpdate(
                ConfirmationMode.HYBRID, 7, 20, 60, 90, 30, 1, 10, 11,
                cancellationCutoff, noShowGrace);
    }

    private RestaurantProfileData restaurant(UUID id) {
        return new RestaurantProfileData(
                id, "restaurant", "Restaurant", null, null, null, "ho-chi-minh", null, null,
                null, null, null, null, null, null, null, "Asia/Ho_Chi_Minh", null,
                "ACTIVE", null, null);
    }
}
