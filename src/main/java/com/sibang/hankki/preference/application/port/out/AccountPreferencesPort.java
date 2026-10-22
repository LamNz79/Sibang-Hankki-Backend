package com.sibang.hankki.preference.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface AccountPreferencesPort {

    Optional<PreferencesData> findByUserId(UUID userId);

    PreferencesData save(UUID userId, PreferencesData preferences);

    void deleteByUserId(UUID userId);

    record PreferencesData(
            String themePreset,
            String displayDensity,
            int tablePageSize,
            String dateFormat,
            String locale) {
    }
}
