package com.rivoo.salon.domain.exception;

public class SalonNotFoundException extends RuntimeException {

    public SalonNotFoundException(String identifier) {
        super("Salon not found: " + identifier);
    }
}
