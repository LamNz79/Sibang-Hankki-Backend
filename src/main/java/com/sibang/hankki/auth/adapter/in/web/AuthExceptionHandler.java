package com.sibang.hankki.auth.adapter.in.web;

import com.sibang.hankki.user.application.exception.DuplicateUseridException;
import com.sibang.hankki.user.application.exception.InvalidCustomerRegistrationException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class AuthExceptionHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InvalidCustomerRegistrationException.class)
    ErrorResponse badRequest(HttpServletRequest request) {
        return ErrorResponse.of(HttpStatus.BAD_REQUEST, request.getRequestURI());
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(DuplicateUseridException.class)
    ErrorResponse conflict(HttpServletRequest request) {
        return ErrorResponse.of(HttpStatus.CONFLICT, request.getRequestURI());
    }

    record ErrorResponse(Instant timestamp, int status, String error, String path) {

        static ErrorResponse of(HttpStatus status, String path) {
            return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), path);
        }
    }
}
