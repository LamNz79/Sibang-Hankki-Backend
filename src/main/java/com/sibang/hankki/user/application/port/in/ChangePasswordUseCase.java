package com.sibang.hankki.user.application.port.in;

import java.util.UUID;

public interface ChangePasswordUseCase {

    void change(UUID userId, ChangePasswordCommand command);

    record ChangePasswordCommand(String currentPassword, String newPassword) {
    }
}
