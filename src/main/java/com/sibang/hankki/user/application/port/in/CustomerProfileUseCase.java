package com.sibang.hankki.user.application.port.in;

import java.util.UUID;

public interface CustomerProfileUseCase {

    CustomerProfile get(UUID userId);

    CustomerProfile update(UUID userId, UpdateCustomerProfileCommand command);

    record UpdateCustomerProfileCommand(String name, String email, String phone) {
    }

    record CustomerProfile(UUID id, String userid, String name, String email, String phone) {
    }
}
