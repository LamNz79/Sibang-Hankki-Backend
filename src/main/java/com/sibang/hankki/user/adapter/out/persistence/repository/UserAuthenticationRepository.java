package com.sibang.hankki.user.adapter.out.persistence.repository;

import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAuthenticationRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByUserid(String userid);

    boolean existsByUserid(String userid);

    Optional<UserEntity> findByIdAndRoleAndDeletedAtIsNull(UUID id, String role);

    Optional<UserEntity> findByUseridAndStatusAndDeletedAtIsNull(String userid, String status);
}
