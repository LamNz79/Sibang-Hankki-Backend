package com.sibang.hankki.user.adapter.in.web;

import com.sibang.hankki.user.application.exception.CustomerProfileNotFoundException;
import com.sibang.hankki.user.application.exception.InvalidCustomerProfileException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class CustomerProfileExceptionHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InvalidCustomerProfileException.class)
    ErrorResponse badRequest(HttpServletRequest request) {
        return ErrorResponse.of(HttpStatus.BAD_REQUEST, request.getRequestURI());
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(CustomerProfileNotFoundException.class)
    ErrorResponse notFound(HttpServletRequest request) {
        return ErrorResponse.of(HttpStatus.NOT_FOUND, request.getRequestURI());
    }

    record ErrorResponse(Instant timestamp, int status, String error, String path) {

        static ErrorResponse of(HttpStatus status, String path) {
            return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), path);
        }
    }
}
