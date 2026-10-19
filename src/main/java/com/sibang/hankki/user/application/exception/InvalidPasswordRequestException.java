package com.sibang.hankki.user.application.exception;

public class InvalidPasswordRequestException extends RuntimeException {

    public InvalidPasswordRequestException(String message) {
        super(message);
    }
}
