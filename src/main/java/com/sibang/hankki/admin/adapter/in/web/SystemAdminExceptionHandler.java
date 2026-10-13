package com.sibang.hankki.admin.adapter.in.web;

import com.sibang.hankki.admin.application.exception.InvalidAdminQueryException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SystemAdminController.class)
public class SystemAdminExceptionHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InvalidAdminQueryException.class)
    void badRequest() {
    }
}
