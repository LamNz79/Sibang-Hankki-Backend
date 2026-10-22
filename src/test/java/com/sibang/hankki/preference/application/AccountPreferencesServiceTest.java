package com.sibang.hankki.preference.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.preference.application.exception.InvalidAccountPreferencesException;
import com.sibang.hankki.preference.application.port.in.AccountPreferencesUseCase.UpdatePreferencesCommand;
import com.sibang.hankki.preference.application.port.out.AccountPreferencesPort;
import com.sibang.hankki.preference.application.port.out.AccountPreferencesPort.PreferencesData;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountPreferencesServiceTest {

    private static final UUID USER_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");

    @Mock
    private AccountPreferencesPort port;
    @InjectMocks
    private AccountPreferencesService service;

    @Test
    void returnsDefaultsWithoutPersistingWhenNoRowExists() {
        given(port.findByUserId(USER_ID)).willReturn(Optional.empty());

        assertThat(service.get(USER_ID))
                .isEqualTo(new com.sibang.hankki.preference.application.port.in.AccountPreferencesUseCase.Preferences(
                        "CORAL", "COMFORTABLE", 20, "DD_MM_YYYY", "vi"));
        verify(port, never()).save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void replacesCompletePreferences() {
        UpdatePreferencesCommand command = validCommand();
        PreferencesData data = new PreferencesData("TEAL", "COMPACT", 50, "MM_DD_YYYY", "en");
        given(port.save(USER_ID, data)).willReturn(data);

        assertThat(service.update(USER_ID, command).themePreset()).isEqualTo("TEAL");
        verify(port).save(USER_ID, data);
    }

    @ParameterizedTest
    @MethodSource("invalidCommands")
    void rejectsMissingOrUnsupportedValues(UpdatePreferencesCommand command) {
        assertThatThrownBy(() -> service.update(USER_ID, command))
                .isInstanceOf(InvalidAccountPreferencesException.class);
        verify(port, never()).save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteDelegatesByAuthenticatedUserId() {
        service.delete(USER_ID);

        verify(port).deleteByUserId(USER_ID);
    }

    private static Stream<UpdatePreferencesCommand> invalidCommands() {
        UpdatePreferencesCommand valid = validCommand();
        return Stream.of(
                null,
                new UpdatePreferencesCommand(null, valid.displayDensity(), 20, valid.dateFormat(), valid.locale()),
                new UpdatePreferencesCommand("PURPLE", valid.displayDensity(), 20, valid.dateFormat(), valid.locale()),
                new UpdatePreferencesCommand(valid.themePreset(), null, 20, valid.dateFormat(), valid.locale()),
                new UpdatePreferencesCommand(valid.themePreset(), "DENSE", 20, valid.dateFormat(), valid.locale()),
                new UpdatePreferencesCommand(
                        valid.themePreset(), valid.displayDensity(), null, valid.dateFormat(), valid.locale()),
                new UpdatePreferencesCommand(
                        valid.themePreset(), valid.displayDensity(), 25, valid.dateFormat(), valid.locale()),
                new UpdatePreferencesCommand(
                        valid.themePreset(), valid.displayDensity(), 20, null, valid.locale()),
                new UpdatePreferencesCommand(
                        valid.themePreset(), valid.displayDensity(), 20, "YYYY_MM_DD", valid.locale()),
                new UpdatePreferencesCommand(
                        valid.themePreset(), valid.displayDensity(), 20, valid.dateFormat(), null),
                new UpdatePreferencesCommand(
                        valid.themePreset(), valid.displayDensity(), 20, valid.dateFormat(), "ko"));
    }

    private static UpdatePreferencesCommand validCommand() {
        return new UpdatePreferencesCommand("TEAL", "COMPACT", 50, "MM_DD_YYYY", "en");
    }
}
