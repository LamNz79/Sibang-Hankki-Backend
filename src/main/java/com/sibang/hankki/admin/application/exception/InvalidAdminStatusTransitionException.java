package com.sibang.hankki.admin.application.exception;

public class InvalidAdminStatusTransitionException extends RuntimeException {

    public InvalidAdminStatusTransitionException(String message) {
        super(message);
    }
}
