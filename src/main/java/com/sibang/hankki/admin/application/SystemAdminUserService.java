package com.sibang.hankki.admin.application;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.exception.AdminUserConflictException;
import com.sibang.hankki.admin.application.exception.InvalidAdminQueryException;
import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase.AdminUser;
import com.sibang.hankki.admin.application.port.in.SystemAdminUserUseCase;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.AdminUserData;
import com.sibang.hankki.admin.application.port.out.SystemAdminUserPort;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemAdminUserService implements SystemAdminUserUseCase {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final SystemAdminUserPort userPort;
    private final PasswordEncoder passwordEncoder;

    public SystemAdminUserService(SystemAdminUserPort userPort, PasswordEncoder passwordEncoder) {
        this.userPort = userPort;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUser findById(UUID userId) {
        return toUser(userPort.findById(userId)
                .orElseThrow(() -> new AdminResourceNotFoundException("User not found")));
    }

    @Override
    @Transactional
    public AdminUser create(CreateUserCommand command) {
        if (command == null) {
            throw invalid("Request body is required");
        }
        String userid = required(command.userid(), "userid", 30);
        if (userid.length() < 3) {
            throw invalid("userid must be between 3 and 30 characters");
        }
        String name = required(command.name(), "name", 120);
        String email = email(command.email());
        String phone = optional(command.phone(), 30);
        String password = command.password();
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw invalid("password must be between 8 and 72 characters");
        }
        if (userPort.existsByUserid(userid)) {
            throw new AdminUserConflictException("userid already exists");
        }
        return toUser(userPort.create(userid, email, passwordEncoder.encode(password), name, phone));
    }

    @Override
    @Transactional
    public AdminUser update(UUID userId, UpdateUserCommand command) {
        if (command == null) {
            throw invalid("Request body is required");
        }
        AdminUserData user = userPort.findByIdForUpdate(userId)
                .orElseThrow(() -> new AdminResourceNotFoundException("User not found"));
        if ("ADMIN".equals(user.role())) {
            throw new AdminUserConflictException("ADMIN profile cannot be changed");
        }
        return toUser(userPort.updateProfile(
                userId, email(command.email()), required(command.name(), "name", 120), optional(command.phone(), 30)));
    }

    @Override
    @Transactional
    public void resetPassword(UUID userId, String newPassword) {
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 8 || newPassword.length() > 72) {
            throw invalid("newPassword must be between 8 and 72 characters");
        }
        AdminUserData user = userPort.findByIdForUpdate(userId)
                .orElseThrow(() -> new AdminResourceNotFoundException("User not found"));
        if ("ADMIN".equals(user.role())) {
            throw new AdminUserConflictException("ADMIN password cannot be reset here");
        }
        userPort.updatePasswordHash(userId, passwordEncoder.encode(newPassword));
    }

    private String email(String value) {
        String normalized = optional(value, 320);
        if (normalized != null && !EMAIL.matcher(normalized).matches()) {
            throw invalid("email must be valid and at most 320 characters");
        }
        return normalized;
    }

    private String required(String value, String field, int maxLength) {
        String normalized = optional(value, maxLength);
        if (normalized == null) {
            throw invalid(field + " is required");
        }
        return normalized;
    }

    private String optional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw invalid("Value is too long");
        }
        return normalized;
    }

    private InvalidAdminQueryException invalid(String message) {
        return new InvalidAdminQueryException(message);
    }

    private AdminUser toUser(AdminUserData user) {
        return new AdminUser(
                user.id(), user.userid(), user.email(), user.name(), user.phone(), user.role(), user.status(),
                user.restaurantId(), user.restaurantName(), user.statusChangedAt(), user.createdAt(), user.updatedAt());
    }
}
