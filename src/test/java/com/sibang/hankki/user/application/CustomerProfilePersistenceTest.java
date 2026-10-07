package com.sibang.hankki.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import com.sibang.hankki.user.adapter.out.persistence.repository.UserAuthenticationRepository;
import com.sibang.hankki.user.application.exception.CustomerProfileNotFoundException;
import com.sibang.hankki.user.application.port.in.CustomerProfileUseCase.UpdateCustomerProfileCommand;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class CustomerProfilePersistenceTest {

    @Autowired
    private CustomerProfileService service;
    @Autowired
    private UserAuthenticationRepository repository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void updatePersistsAllowedFieldsAndLeavesIdentityUntouched() {
        UserEntity user = repository.saveAndFlush(customer("profile-persistence-user"));

        var updated = service.update(user.getId(), new UpdateCustomerProfileCommand(
                "  Updated Name  ", "  updated@example.com  ", "  0900000000  "));
        entityManager.flush();
        entityManager.clear();
        UserEntity persisted = repository.findById(user.getId()).orElseThrow();

        assertEquals("profile-persistence-user", updated.userid());
        assertEquals("profile-persistence-user", persisted.getUserid());
        assertEquals("Updated Name", persisted.getName());
        assertEquals("updated@example.com", persisted.getEmail());
        assertEquals("0900000000", persisted.getPhone());
        assertEquals("CUSTOMER", persisted.getRole());
        assertEquals("ACTIVE", persisted.getStatus());
        assertNull(persisted.getRestaurantId());
    }

    @Test
    void updateIsScopedToAuthenticatedUserId() {
        UserEntity first = repository.saveAndFlush(customer("profile-owner-one"));
        UserEntity second = repository.saveAndFlush(customer("profile-owner-two"));

        service.update(first.getId(), new UpdateCustomerProfileCommand(
                "First Updated", "first@example.com", null));
        entityManager.flush();
        entityManager.clear();

        assertEquals("First Updated", repository.findById(first.getId()).orElseThrow().getName());
        assertEquals("Customer Name", repository.findById(second.getId()).orElseThrow().getName());
    }

    @Test
    void softDeletedUserIsNotReadable() {
        UserEntity user = repository.saveAndFlush(customer("profile-deleted-user"));
        jdbcTemplate.update("update users set deleted_at = current_timestamp where id = ?", user.getId());
        entityManager.clear();

        assertThrows(CustomerProfileNotFoundException.class, () -> service.get(user.getId()));
    }

    private UserEntity customer(String userid) {
        return new UserEntity(userid, userid + "@example.com", "hash", "Customer Name");
    }
}
