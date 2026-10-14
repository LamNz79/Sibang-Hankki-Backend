package com.sibang.hankki.admin.application.exception;

public class AdminUserConflictException extends RuntimeException {

    public AdminUserConflictException(String message) {
        super(message);
    }
}
