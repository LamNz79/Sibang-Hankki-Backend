package com.sibang.hankki.restaurant.adapter.in.web;

import com.sibang.hankki.restaurant.application.exception.BookingSettingsNotConfiguredException;
import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.exception.MenuResourceNotFoundException;
import com.sibang.hankki.restaurant.application.exception.OwnerMediaNotFoundException;
import com.sibang.hankki.restaurant.application.exception.RestaurantNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class RestaurantExceptionHandler {

    @ExceptionHandler(InvalidBookingRequestException.class)
    ResponseEntity<Void> invalidBookingRequest(InvalidBookingRequestException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    @ExceptionHandler(RestaurantNotFoundException.class)
    ResponseEntity<Void> restaurantNotFound(RestaurantNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @ExceptionHandler(MenuResourceNotFoundException.class)
    ResponseEntity<Void> menuResourceNotFound(MenuResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @ExceptionHandler(OwnerMediaNotFoundException.class)
    ResponseEntity<Void> ownerMediaNotFound(OwnerMediaNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @ExceptionHandler(BookingSettingsNotConfiguredException.class)
    ResponseEntity<Void> bookingSettingsNotConfigured(BookingSettingsNotConfiguredException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
}
