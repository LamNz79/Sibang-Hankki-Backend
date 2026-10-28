package com.sibang.hankki.preference.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.sibang.hankki.preference.application.port.in.AccountPreferencesUseCase.UpdatePreferencesCommand;
import com.sibang.hankki.preference.application.port.out.AccountPreferencesPort;
import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import com.sibang.hankki.user.adapter.out.persistence.repository.UserAuthenticationRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class AccountPreferencesPersistenceTest {

    @Autowired
    private AccountPreferencesService service;
    @Autowired
    private AccountPreferencesPort port;
    @Autowired
    private UserAuthenticationRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @Test
    void createsThenUpdatesOnePersistentRowAcrossServiceInstances() {
        UUID userId = customer("preferences-persistent").getId();

        service.update(userId, command("#C45870", "COMFORTABLE", 10, "DD_MM_YYYY", "vi"));
        service.update(userId, command("#2563EB", "COMPACT", 50, "MM_DD_YYYY", "ko"));
        entityManager.flush();
        entityManager.clear();

        assertThat(rowCount(userId)).isEqualTo(1);
        AccountPreferencesService restartedService = new AccountPreferencesService(port);
        assertThat(restartedService.get(userId).themeColor()).isEqualTo("#2563EB");
        assertThat(restartedService.get(userId).tablePageSize()).isEqualTo(50);
        assertThat(restartedService.get(userId).locale()).isEqualTo("ko");
        assertThat(storedLocale(userId)).isEqualTo("ko");
    }

    @Test
    void usersAreIsolatedAndDeleteIsIdempotent() {
        UUID first = customer("preferences-first").getId();
        UUID second = customer("preferences-second").getId();
        service.update(first, command("#3a7c6e", "COMPACT", 10, "MM_DD_YYYY", "en"));
        service.update(second, command("#C45870", "COMFORTABLE", 50, "DD_MM_YYYY", "vi"));
        entityManager.flush();
        entityManager.clear();

        assertThat(storedThemeColor(first)).isEqualTo("#3A7C6E");

        service.delete(first);
        service.delete(first);
        entityManager.flush();
        entityManager.clear();

        assertThat(service.get(first).themeColor()).isEqualTo("#B84A2F");
        assertThat(service.get(first).tablePageSize()).isEqualTo(20);
        assertThat(service.get(second).themeColor()).isEqualTo("#C45870");
        assertThat(rowCount(first)).isZero();
        assertThat(rowCount(second)).isEqualTo(1);
    }

    private UserEntity customer(String userid) {
        return userRepository.saveAndFlush(new UserEntity(
                userid, userid + "@example.com", "hash", "Preference Customer"));
    }

    private UpdatePreferencesCommand command(
            String theme, String density, int pageSize, String dateFormat, String locale) {
        return new UpdatePreferencesCommand(theme, density, pageSize, dateFormat, locale);
    }

    private int rowCount(UUID userId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from user_preferences where user_id = ?", Integer.class, userId);
    }

    private String storedThemeColor(UUID userId) {
        return jdbcTemplate.queryForObject(
                "select theme_color from user_preferences where user_id = ?", String.class, userId);
    }

    private String storedLocale(UUID userId) {
        return jdbcTemplate.queryForObject(
                "select locale from user_preferences where user_id = ?", String.class, userId);
    }
}
