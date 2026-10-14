package com.sibang.hankki.location.application.exception;

public class LocationCatalogUnavailableException extends RuntimeException {

    public LocationCatalogUnavailableException() {
        super("Location catalog is temporarily unavailable");
    }
}
