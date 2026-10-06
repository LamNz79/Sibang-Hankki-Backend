package com.sibang.hankki.restaurant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.model.OwnerSettingsUpdate;
import com.sibang.hankki.restaurant.application.port.out.OwnerSettingsPort;
import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnerSettingsServiceTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    private OwnerSettingsPort port;

    private OwnerSettingsService service;

    @BeforeEach
    void setUp() {
        service = new OwnerSettingsService(port);
    }

    @Test
    void trimsProfileFieldsAndDelegatesValidatedSettings() {
        OwnerSettingsUpdate update = update(ConfirmationMode.HYBRID, 6);

        service.update(RESTAURANT_ID, update);

        ArgumentCaptor<OwnerSettingsUpdate> captured = ArgumentCaptor.forClass(OwnerSettingsUpdate.class);
        verify(port).update(org.mockito.ArgumentMatchers.eq(RESTAURANT_ID), captured.capture());
        assertThat(captured.getValue().name()).isEqualTo("The Royal Pavilion");
        assertThat(captured.getValue().description()).isNull();
        assertThat(captured.getValue().manualConfirmationMinPartySize()).isEqualTo(6);
    }

    @Test
    void rejectsInvalidHybridSettingsWithoutWriting() {
        assertThatThrownBy(() -> service.update(RESTAURANT_ID, update(ConfirmationMode.HYBRID, null)))
                .isInstanceOf(InvalidBookingRequestException.class);

        verify(port, never()).update(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsValuesThatCannotFitDatabaseSmallint() {
        OwnerSettingsUpdate update = new OwnerSettingsUpdate(
                "Restaurant", null, null, null, null, null, null, null, null,
                40, Short.MAX_VALUE + 1, 90, ConfirmationMode.AUTO, null,
                30, 1, 6, 10, 120);

        assertThatThrownBy(() -> service.update(RESTAURANT_ID, update))
                .isInstanceOf(InvalidBookingRequestException.class);
        verify(port, never()).update(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private OwnerSettingsUpdate update(ConfirmationMode mode, Integer manualThreshold) {
        return new OwnerSettingsUpdate(
                "  The Royal Pavilion  ", "  ", " Chinese ", " District 1 ", " District 1 ",
                " 123 Street ", " 0900000000 ", " owner@example.com ", " $$$ ",
                48, 30, 90, mode, manualThreshold, 30, 1, 6, 10, 120);
    }
}
