package com.sibang.hankki.user.application.exception;

public class InvalidCustomerRegistrationException extends RuntimeException {

    public InvalidCustomerRegistrationException(String message) {
        super(message);
    }
}
