package com.sibang.hankki.preference.application;

import com.sibang.hankki.preference.application.exception.InvalidAccountPreferencesException;
import com.sibang.hankki.preference.application.port.in.AccountPreferencesUseCase;
import com.sibang.hankki.preference.application.port.out.AccountPreferencesPort;
import com.sibang.hankki.preference.application.port.out.AccountPreferencesPort.PreferencesData;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountPreferencesService implements AccountPreferencesUseCase {

    private static final Set<String> THEME_PRESETS = Set.of("CORAL", "ROSE", "TEAL", "BLUE");
    private static final Set<String> DISPLAY_DENSITIES = Set.of("COMFORTABLE", "COMPACT");
    private static final Set<Integer> TABLE_PAGE_SIZES = Set.of(10, 20, 50);
    private static final Set<String> DATE_FORMATS = Set.of("DD_MM_YYYY", "MM_DD_YYYY");
    private static final Set<String> LOCALES = Set.of("vi", "en");
    private static final Preferences DEFAULTS = new Preferences(
            "CORAL", "COMFORTABLE", 20, "DD_MM_YYYY", "vi");

    private final AccountPreferencesPort port;

    public AccountPreferencesService(AccountPreferencesPort port) {
        this.port = port;
    }

    @Override
    @Transactional(readOnly = true)
    public Preferences get(UUID userId) {
        return port.findByUserId(userId).map(this::toPreferences).orElse(DEFAULTS);
    }

    @Override
    @Transactional
    public Preferences update(UUID userId, UpdatePreferencesCommand command) {
        validate(command);
        PreferencesData saved = port.save(userId, new PreferencesData(
                command.themePreset(),
                command.displayDensity(),
                command.tablePageSize(),
                command.dateFormat(),
                command.locale()));
        return toPreferences(saved);
    }

    @Override
    @Transactional
    public void delete(UUID userId) {
        port.deleteByUserId(userId);
    }

    private void validate(UpdatePreferencesCommand command) {
        if (command == null) {
            throw invalid("Request body is required");
        }
        requireSupported(THEME_PRESETS, command.themePreset(), "themePreset");
        requireSupported(DISPLAY_DENSITIES, command.displayDensity(), "displayDensity");
        requireSupported(TABLE_PAGE_SIZES, command.tablePageSize(), "tablePageSize");
        requireSupported(DATE_FORMATS, command.dateFormat(), "dateFormat");
        requireSupported(LOCALES, command.locale(), "locale");
    }

    private <T> void requireSupported(Set<T> supported, T value, String field) {
        if (value == null || !supported.contains(value)) {
            throw invalid(field + " is required and must be a supported value");
        }
    }

    private InvalidAccountPreferencesException invalid(String message) {
        return new InvalidAccountPreferencesException(message);
    }

    private Preferences toPreferences(PreferencesData data) {
        return new Preferences(
                data.themePreset(),
                data.displayDensity(),
                data.tablePageSize(),
                data.dateFormat(),
                data.locale());
    }
}
