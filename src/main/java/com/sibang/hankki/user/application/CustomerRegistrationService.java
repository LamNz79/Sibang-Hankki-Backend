package com.sibang.hankki.user.application;

import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import com.sibang.hankki.user.adapter.out.persistence.repository.UserAuthenticationRepository;
import com.sibang.hankki.user.application.exception.DuplicateUseridException;
import com.sibang.hankki.user.application.exception.InvalidCustomerRegistrationException;
import com.sibang.hankki.user.application.port.in.RegisterCustomerUseCase;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerRegistrationService implements RegisterCustomerUseCase {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserAuthenticationRepository repository;
    private final PasswordEncoder passwordEncoder;

    public CustomerRegistrationService(
            UserAuthenticationRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public RegisteredCustomer register(RegisterCustomerCommand command) {
        if (command == null) {
            throw new InvalidCustomerRegistrationException("Request body is required");
        }
        String userid = trim(command.userid());
        String email = trim(command.email());
        String name = trim(command.name());
        String password = command.password();
        validate(userid, email, name, password);
        if (repository.existsByUserid(userid)) {
            throw new DuplicateUseridException();
        }

        UserEntity saved;
        try {
            saved = repository.saveAndFlush(new UserEntity(
                    userid, email, passwordEncoder.encode(password), name));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateUseridException();
        }
        return new RegisteredCustomer(
                saved.getId(), saved.getUserid(), saved.getEmail(), saved.getName(), saved.getRole());
    }

    private void validate(String userid, String email, String name, String password) {
        if (userid == null || userid.length() < 3 || userid.length() > 30) {
            throw new InvalidCustomerRegistrationException("userid must be between 3 and 30 characters");
        }
        if (name == null || name.length() > 120) {
            throw new InvalidCustomerRegistrationException("name is required and must be at most 120 characters");
        }
        if (email == null || email.length() > 320 || !EMAIL.matcher(email).matches()) {
            throw new InvalidCustomerRegistrationException("email must be valid and at most 320 characters");
        }
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new InvalidCustomerRegistrationException("password must be between 8 and 72 characters");
        }
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
