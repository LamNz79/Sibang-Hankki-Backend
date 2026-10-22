package com.sibang.hankki.preference.adapter.in.web;

import com.sibang.hankki.preference.application.exception.InvalidAccountPreferencesException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AccountPreferencesController.class)
class AccountPreferencesExceptionHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InvalidAccountPreferencesException.class)
    ErrorResponse badRequest(HttpServletRequest request) {
        return new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                request.getRequestURI());
    }

    record ErrorResponse(Instant timestamp, int status, String error, String path) {
    }
}
