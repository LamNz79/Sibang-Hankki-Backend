package com.sibang.hankki.preference.adapter.out.persistence.repository;

import com.sibang.hankki.preference.adapter.out.persistence.entity.UserPreferencesEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferencesRepository extends JpaRepository<UserPreferencesEntity, UUID> {
}
