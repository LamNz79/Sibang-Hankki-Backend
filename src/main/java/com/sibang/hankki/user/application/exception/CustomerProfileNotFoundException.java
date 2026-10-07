package com.sibang.hankki.user.application.exception;

public class CustomerProfileNotFoundException extends RuntimeException {

    public CustomerProfileNotFoundException() {
        super("Customer profile not found");
    }
}
