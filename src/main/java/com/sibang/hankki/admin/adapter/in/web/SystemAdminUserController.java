package com.sibang.hankki.admin.adapter.in.web;

import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase.AdminUser;
import com.sibang.hankki.admin.application.port.in.SystemAdminUserUseCase;
import com.sibang.hankki.admin.application.port.in.SystemAdminUserUseCase.CreateUserCommand;
import com.sibang.hankki.admin.application.port.in.SystemAdminUserUseCase.UpdateUserCommand;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class SystemAdminUserController {

    private final SystemAdminUserUseCase userUseCase;

    public SystemAdminUserController(SystemAdminUserUseCase userUseCase) {
        this.userUseCase = userUseCase;
    }

    @GetMapping("/{userId}")
    public AdminUser findById(@PathVariable UUID userId) {
        return userUseCase.findById(userId);
    }

    @PostMapping
    public ResponseEntity<AdminUser> create(@RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userUseCase.create(request.toCommand()));
    }

    @PutMapping("/{userId}")
    public AdminUser update(@PathVariable UUID userId, @RequestBody UpdateUserRequest request) {
        return userUseCase.update(userId, request.toCommand());
    }

    @PatchMapping("/{userId}/password")
    public ResponseEntity<Void> resetPassword(
            @PathVariable UUID userId,
            @RequestBody(required = false) ResetPasswordRequest request) {
        userUseCase.resetPassword(userId, request == null ? null : request.newPassword());
        return ResponseEntity.noContent().build();
    }

    record CreateUserRequest(String userid, String email, String password, String name, String phone) {
        CreateUserCommand toCommand() {
            return new CreateUserCommand(userid, email, password, name, phone);
        }
    }

    record UpdateUserRequest(String email, String name, String phone) {
        UpdateUserCommand toCommand() {
            return new UpdateUserCommand(email, name, phone);
        }
    }

    record ResetPasswordRequest(String newPassword) {

        @Override
        public String toString() {
            return "ResetPasswordRequest[newPassword=[PROTECTED]]";
        }
    }
}
