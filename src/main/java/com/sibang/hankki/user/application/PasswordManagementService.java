package com.sibang.hankki.user.application;

import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import com.sibang.hankki.user.adapter.out.persistence.repository.UserAuthenticationRepository;
import com.sibang.hankki.user.application.exception.IncorrectCurrentPasswordException;
import com.sibang.hankki.user.application.exception.InvalidPasswordRequestException;
import com.sibang.hankki.user.application.port.in.ChangePasswordUseCase;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordManagementService implements ChangePasswordUseCase {

    private static final String ACTIVE = "ACTIVE";

    private final UserAuthenticationRepository repository;
    private final PasswordEncoder passwordEncoder;

    public PasswordManagementService(
            UserAuthenticationRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void change(UUID userId, ChangePasswordCommand command) {
        validate(command);
        UserEntity user = repository.findByIdAndStatusAndDeletedAtIsNull(userId, ACTIVE)
                .orElseThrow(IncorrectCurrentPasswordException::new);
        if (!passwordEncoder.matches(command.currentPassword(), user.getPasswordHash())) {
            throw new IncorrectCurrentPasswordException();
        }
        user.updatePasswordHash(passwordEncoder.encode(command.newPassword()));
    }

    private void validate(ChangePasswordCommand command) {
        if (command == null || command.currentPassword() == null || command.currentPassword().isBlank()) {
            throw new InvalidPasswordRequestException("currentPassword is required");
        }
        if (command.newPassword() == null
                || command.newPassword().isBlank()
                || command.newPassword().length() < 8
                || command.newPassword().length() > 72) {
            throw new InvalidPasswordRequestException("newPassword must be between 8 and 72 characters");
        }
    }
}
