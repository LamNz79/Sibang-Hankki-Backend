package com.sibang.hankki.user.application.exception;

public class DuplicateUseridException extends RuntimeException {

    public DuplicateUseridException() {
        super("userid already exists");
    }
}
