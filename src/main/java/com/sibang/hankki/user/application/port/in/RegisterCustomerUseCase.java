package com.sibang.hankki.user.application.port.in;

import java.util.UUID;

public interface RegisterCustomerUseCase {

    RegisteredCustomer register(RegisterCustomerCommand command);

    record RegisterCustomerCommand(String userid, String email, String name, String password) {
    }

    record RegisteredCustomer(UUID id, String userid, String email, String name, String role) {
    }
}
