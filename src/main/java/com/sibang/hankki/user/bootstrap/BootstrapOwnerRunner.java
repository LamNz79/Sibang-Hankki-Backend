package com.sibang.hankki.user.bootstrap;

import com.sibang.hankki.restaurant.adapter.out.persistence.repository.RestaurantCatalogRepository;
import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import com.sibang.hankki.user.adapter.out.persistence.repository.UserAuthenticationRepository;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConfigurationProperties(prefix = "app.bootstrap-owner")
public class BootstrapOwnerRunner implements ApplicationRunner {

    private static final String OWNER = "OWNER";

    private final UserAuthenticationRepository userRepository;
    private final RestaurantCatalogRepository restaurantRepository;
    private final PasswordEncoder passwordEncoder;

    private boolean enabled;
    private String userid;
    private String password;
    private String restaurantId;

    public BootstrapOwnerRunner(
            UserAuthenticationRepository userRepository,
            RestaurantCatalogRepository restaurantRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        String configuredUserid = required(userid, "APP_BOOTSTRAP_OWNER_USERID");
        String configuredPassword = required(password, "APP_BOOTSTRAP_OWNER_PASSWORD");
        UUID configuredRestaurantId = restaurantId(required(
                restaurantId, "APP_BOOTSTRAP_OWNER_RESTAURANT_ID"));

        if (configuredUserid.length() > 30) {
            throw new IllegalStateException("APP_BOOTSTRAP_OWNER_USERID must not exceed 30 characters");
        }
        if (!restaurantRepository.existsById(configuredRestaurantId)) {
            throw new IllegalStateException("APP_BOOTSTRAP_OWNER_RESTAURANT_ID does not identify a restaurant");
        }

        UserEntity existing = userRepository.findByUserid(configuredUserid).orElse(null);
        if (existing != null) {
            if (OWNER.equals(existing.getRole()) && configuredRestaurantId.equals(existing.getRestaurantId())) {
                return;
            }
            throw new IllegalStateException(
                    "APP_BOOTSTRAP_OWNER_USERID already belongs to another role or restaurant");
        }

        userRepository.save(new UserEntity(
                configuredUserid,
                passwordEncoder.encode(configuredPassword),
                "Bootstrap Owner",
                OWNER,
                configuredRestaurantId));
    }

    private String required(String value, String variableName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variableName + " is required when APP_BOOTSTRAP_OWNER_ENABLED=true");
        }
        return value;
    }

    private UUID restaurantId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("APP_BOOTSTRAP_OWNER_RESTAURANT_ID must be a valid UUID", exception);
        }
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

    public void setRestaurantId(String restaurantId) {
        this.restaurantId = restaurantId;
    }
}
