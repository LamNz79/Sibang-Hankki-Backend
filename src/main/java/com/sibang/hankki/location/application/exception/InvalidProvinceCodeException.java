package com.sibang.hankki.location.application.exception;

public class InvalidProvinceCodeException extends RuntimeException {

    public InvalidProvinceCodeException() {
        super("provinceCode is invalid");
    }
}
