package com.sibang.hankki.preference.adapter.out.persistence;

import com.sibang.hankki.preference.adapter.out.persistence.entity.UserPreferencesEntity;
import com.sibang.hankki.preference.adapter.out.persistence.repository.UserPreferencesRepository;
import com.sibang.hankki.preference.application.port.out.AccountPreferencesPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AccountPreferencesPersistenceAdapter implements AccountPreferencesPort {

    private final UserPreferencesRepository repository;

    public AccountPreferencesPersistenceAdapter(UserPreferencesRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<PreferencesData> findByUserId(UUID userId) {
        return repository.findById(userId).map(this::toData);
    }

    @Override
    public PreferencesData save(UUID userId, PreferencesData preferences) {
        UserPreferencesEntity entity = repository.findById(userId)
                .orElseGet(() -> new UserPreferencesEntity(
                        userId,
                        preferences.themePreset(),
                        preferences.displayDensity(),
                        preferences.tablePageSize(),
                        preferences.dateFormat(),
                        preferences.locale()));
        entity.replace(
                preferences.themePreset(),
                preferences.displayDensity(),
                preferences.tablePageSize(),
                preferences.dateFormat(),
                preferences.locale());
        return toData(repository.save(entity));
    }

    @Override
    public void deleteByUserId(UUID userId) {
        repository.deleteById(userId);
    }

    private PreferencesData toData(UserPreferencesEntity entity) {
        return new PreferencesData(
                entity.getThemePreset(),
                entity.getDisplayDensity(),
                entity.getTablePageSize(),
                entity.getDateFormat(),
                entity.getLocale());
    }
}
