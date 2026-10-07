package com.sibang.hankki.user.application;

import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import com.sibang.hankki.user.adapter.out.persistence.repository.UserAuthenticationRepository;
import com.sibang.hankki.user.application.exception.CustomerProfileNotFoundException;
import com.sibang.hankki.user.application.exception.InvalidCustomerProfileException;
import com.sibang.hankki.user.application.port.in.CustomerProfileUseCase;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerProfileService implements CustomerProfileUseCase {

    private static final String CUSTOMER = "CUSTOMER";
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserAuthenticationRepository repository;

    public CustomerProfileService(UserAuthenticationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerProfile get(UUID userId) {
        return toProfile(findCustomer(userId));
    }

    @Override
    @Transactional
    public CustomerProfile update(UUID userId, UpdateCustomerProfileCommand command) {
        if (command == null) {
            throw new InvalidCustomerProfileException("Request body is required");
        }
        String name = trim(command.name());
        String email = trim(command.email());
        String phone = trim(command.phone());
        validate(name, email, phone);

        UserEntity user = findCustomer(userId);
        user.updateProfile(name, email, phone);
        return toProfile(user);
    }

    private UserEntity findCustomer(UUID userId) {
        return repository.findByIdAndRoleAndDeletedAtIsNull(userId, CUSTOMER)
                .orElseThrow(CustomerProfileNotFoundException::new);
    }

    private void validate(String name, String email, String phone) {
        if (name == null || name.length() > 120) {
            throw new InvalidCustomerProfileException("name is required and must be at most 120 characters");
        }
        if (email == null || email.length() > 320 || !EMAIL.matcher(email).matches()) {
            throw new InvalidCustomerProfileException("email must be valid and at most 320 characters");
        }
        if (phone != null && phone.length() > 30) {
            throw new InvalidCustomerProfileException("phone must be at most 30 characters");
        }
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private CustomerProfile toProfile(UserEntity user) {
        return new CustomerProfile(
                user.getId(), user.getUserid(), user.getName(), user.getEmail(), user.getPhone());
    }
}
