package com.sibang.hankki.auth;

import java.util.UUID;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

public final class SessionUser extends User {

    private final UUID id;
    private final String role;
    private final UUID restaurantId;

    SessionUser(UUID id, String userid, String passwordHash, String role, UUID restaurantId) {
        super(userid, passwordHash, AuthorityUtils.createAuthorityList("ROLE_" + role));
        this.id = id;
        this.role = role;
        this.restaurantId = restaurantId;
    }

    public UUID id() {
        return id;
    }

    public String userid() {
        return getUsername();
    }

    public String role() {
        return role;
    }

    public UUID restaurantId() {
        return restaurantId;
    }
}
