package com.sibang.hankki.admin.adapter.in.web;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.exception.InvalidAdminQueryException;
import com.sibang.hankki.admin.application.exception.InvalidAdminStatusTransitionException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SystemAdminController.class)
public class SystemAdminExceptionHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InvalidAdminQueryException.class)
    ErrorResponse badRequest(HttpServletRequest request) {
        return ErrorResponse.of(HttpStatus.BAD_REQUEST, request.getRequestURI());
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(AdminResourceNotFoundException.class)
    ErrorResponse notFound(HttpServletRequest request) {
        return ErrorResponse.of(HttpStatus.NOT_FOUND, request.getRequestURI());
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(InvalidAdminStatusTransitionException.class)
    ErrorResponse conflict(HttpServletRequest request) {
        return ErrorResponse.of(HttpStatus.CONFLICT, request.getRequestURI());
    }

    record ErrorResponse(Instant timestamp, int status, String error, String path) {

        static ErrorResponse of(HttpStatus status, String path) {
            return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), path);
        }
    }
}
