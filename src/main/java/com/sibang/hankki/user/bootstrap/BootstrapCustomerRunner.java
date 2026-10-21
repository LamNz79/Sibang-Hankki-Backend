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
@ConfigurationProperties(prefix = "app.bootstrap-customer")
public class BootstrapCustomerRunner implements ApplicationRunner {

    private final UserAuthenticationRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private boolean enabled;
    private String userid;
    private String password;
    private String name;
    private String email;

    public BootstrapCustomerRunner(
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

        String configuredUserid = required(userid, "APP_BOOTSTRAP_CUSTOMER_USERID").strip();
        String configuredPassword = required(password, "APP_BOOTSTRAP_CUSTOMER_PASSWORD");
        if (configuredUserid.length() < 3 || configuredUserid.length() > 30) {
            throw new IllegalStateException("APP_BOOTSTRAP_CUSTOMER_USERID must be between 3 and 30 characters");
        }
        if (configuredPassword.length() < 8 || configuredPassword.length() > 72) {
            throw new IllegalStateException("APP_BOOTSTRAP_CUSTOMER_PASSWORD must be between 8 and 72 characters");
        }

        if (userRepository.findByUserid(configuredUserid).isPresent()) {
            return;
        }

        String configuredName = optional(name);
        String configuredEmail = optional(email);
        if (configuredName == null) {
            configuredName = configuredUserid;
        }
        if (configuredName.length() > 120) {
            throw new IllegalStateException("APP_BOOTSTRAP_CUSTOMER_NAME must not exceed 120 characters");
        }
        if (configuredEmail != null && configuredEmail.length() > 320) {
            throw new IllegalStateException("APP_BOOTSTRAP_CUSTOMER_EMAIL must not exceed 320 characters");
        }

        userRepository.save(new UserEntity(
                configuredUserid,
                configuredEmail,
                passwordEncoder.encode(configuredPassword),
                configuredName));
    }

    private String required(String value, String variableName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variableName + " is required when APP_BOOTSTRAP_CUSTOMER_ENABLED=true");
        }
        return value;
    }

    private String optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
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

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
