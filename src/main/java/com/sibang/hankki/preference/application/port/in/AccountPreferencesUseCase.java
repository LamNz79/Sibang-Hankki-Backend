package com.sibang.hankki.preference.application.port.in;

import java.util.UUID;

public interface AccountPreferencesUseCase {

    Preferences get(UUID userId);

    Preferences update(UUID userId, UpdatePreferencesCommand command);

    void delete(UUID userId);

    record UpdatePreferencesCommand(
            String themePreset,
            String displayDensity,
            Integer tablePageSize,
            String dateFormat,
            String locale) {
    }

    record Preferences(
            String themePreset,
            String displayDensity,
            int tablePageSize,
            String dateFormat,
            String locale) {
    }
}
