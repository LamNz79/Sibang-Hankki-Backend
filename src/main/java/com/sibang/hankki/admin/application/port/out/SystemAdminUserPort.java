package com.sibang.hankki.admin.application.port.out;

import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.AdminUserData;
import java.util.Optional;
import java.util.UUID;

public interface SystemAdminUserPort {

    Optional<AdminUserData> findById(UUID userId);

    Optional<AdminUserData> findByIdForUpdate(UUID userId);

    boolean existsByUserid(String userid);

    AdminUserData create(String userid, String email, String passwordHash, String name, String phone);

    AdminUserData updateProfile(UUID userId, String email, String name, String phone);
}
