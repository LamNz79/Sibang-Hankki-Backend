package com.sibang.hankki.user.application.exception;

public class IncorrectCurrentPasswordException extends RuntimeException {

    public IncorrectCurrentPasswordException() {
        super("Current password is incorrect");
    }
}
