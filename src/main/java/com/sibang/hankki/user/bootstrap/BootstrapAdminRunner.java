package com.sibang.hankki.user.bootstrap;

import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import com.sibang.hankki.user.adapter.out.persistence.repository.UserAuthenticationRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConfigurationProperties(prefix = "app.bootstrap-admin")
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final String ADMIN = "ADMIN";

    private final UserAuthenticationRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private boolean enabled;
    private String userid;
    private String password;

    public BootstrapAdminRunner(
            UserAuthenticationRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        String configuredUserid = required(userid, "APP_BOOTSTRAP_ADMIN_USERID").strip();
        String configuredPassword = required(password, "APP_BOOTSTRAP_ADMIN_PASSWORD");
        if (configuredUserid.length() < 3 || configuredUserid.length() > 30) {
            throw new IllegalStateException("APP_BOOTSTRAP_ADMIN_USERID must be between 3 and 30 characters");
        }

        UserEntity existing = userRepository.findByUserid(configuredUserid).orElse(null);
        if (existing != null) {
            if (ADMIN.equals(existing.getRole()) && existing.getRestaurantId() == null) {
                return;
            }
            throw new IllegalStateException(
                    "APP_BOOTSTRAP_ADMIN_USERID already belongs to another role or restaurant");
        }

        userRepository.save(new UserEntity(
                configuredUserid,
                passwordEncoder.encode(configuredPassword),
                "System Administrator",
                ADMIN,
                null));
    }

    private String required(String value, String variableName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variableName + " is required when APP_BOOTSTRAP_ADMIN_ENABLED=true");
        }
        return value;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setUserid(String userid) {
        this.userid = userid;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
