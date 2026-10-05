package com.sibang.hankki.auth;

import com.sibang.hankki.user.adapter.out.persistence.entity.UserEntity;
import com.sibang.hankki.user.adapter.out.persistence.repository.UserAuthenticationRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class OwnerSessionUserDetailsService implements UserDetailsService {

    private static final String ACTIVE = "ACTIVE";

    private final UserAuthenticationRepository repository;

    public OwnerSessionUserDetailsService(UserAuthenticationRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String userid) throws UsernameNotFoundException {
        UserEntity user = repository.findByUseridAndStatusAndDeletedAtIsNull(userid, ACTIVE)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return new SessionUser(
                user.getId(), user.getUserid(), user.getPasswordHash(), user.getRole(), user.getRestaurantId());
    }
}
