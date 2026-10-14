package com.sibang.hankki.admin.application.port.in;

import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase.AdminUser;
import java.util.UUID;

public interface SystemAdminUserUseCase {

    AdminUser findById(UUID userId);

    AdminUser create(CreateUserCommand command);

    AdminUser update(UUID userId, UpdateUserCommand command);

    record CreateUserCommand(String userid, String email, String password, String name, String phone) {
    }

    record UpdateUserCommand(String email, String name, String phone) {
    }
}
