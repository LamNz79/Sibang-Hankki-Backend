package com.sibang.hankki.admin.adapter.out.persistence.repository;

import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminUserRepository
        extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {

    Optional<UserEntity> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByUserid(String userid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :id and u.deletedAt is null")
    Optional<UserEntity> findByIdAndDeletedAtIsNullForUpdate(@Param("id") UUID id);

    @Query(value = """
            select u from UserEntity u, RestaurantEntity r
            where u.deletedAt is null
              and u.role in ('OWNER', 'STAFF')
              and u.restaurantId = r.id
              and r.deletedAt is null
              and (:role is null or u.role = :role)
              and (:restaurantId is null or u.restaurantId = :restaurantId)
              and (:query is null
                   or lower(u.userid) like :query escape '\\'
                   or lower(u.name) like :query escape '\\'
                   or lower(u.email) like :query escape '\\'
                   or lower(r.name) like :query escape '\\')
            """,
            countQuery = """
            select count(u) from UserEntity u, RestaurantEntity r
            where u.deletedAt is null
              and u.role in ('OWNER', 'STAFF')
              and u.restaurantId = r.id
              and r.deletedAt is null
              and (:role is null or u.role = :role)
              and (:restaurantId is null or u.restaurantId = :restaurantId)
              and (:query is null
                   or lower(u.userid) like :query escape '\\'
                   or lower(u.name) like :query escape '\\'
                   or lower(u.email) like :query escape '\\'
                   or lower(r.name) like :query escape '\\')
            """)
    Page<UserEntity> findAssignments(
            @Param("query") String query,
            @Param("role") String role,
            @Param("restaurantId") UUID restaurantId,
            Pageable pageable);
}
