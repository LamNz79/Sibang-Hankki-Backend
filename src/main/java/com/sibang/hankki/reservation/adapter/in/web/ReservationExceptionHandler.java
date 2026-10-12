package com.sibang.hankki.reservation.adapter.in.web;

import com.sibang.hankki.reservation.application.exception.InvalidReservationRequestException;
import com.sibang.hankki.reservation.application.exception.InvalidReservationStateException;
import com.sibang.hankki.reservation.application.exception.NoActiveBookingSlotException;
import com.sibang.hankki.reservation.application.exception.ReservationBookingSettingsNotConfiguredException;
import com.sibang.hankki.reservation.application.exception.ReservationCapacityUnavailableException;
import com.sibang.hankki.reservation.application.exception.ReservationIdempotencyConflictException;
import com.sibang.hankki.reservation.application.exception.ReservationNotFoundException;
import com.sibang.hankki.reservation.application.exception.ReservationSlotNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ReservationExceptionHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InvalidReservationRequestException.class)
    void badRequest() {
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler({ReservationNotFoundException.class, ReservationSlotNotFoundException.class})
    void notFound() {
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler({
            ReservationBookingSettingsNotConfiguredException.class,
            ReservationCapacityUnavailableException.class,
            ReservationIdempotencyConflictException.class,
            InvalidReservationStateException.class
    })
    void conflict() {
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(NoActiveBookingSlotException.class)
    ErrorResponse noActiveSlot(NoActiveBookingSlotException exception, HttpServletRequest request) {
        return new ErrorResponse(
                Instant.now(), HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT.getReasonPhrase(),
                exception.getMessage(), request.getRequestURI());
    }

    record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
    }
}
