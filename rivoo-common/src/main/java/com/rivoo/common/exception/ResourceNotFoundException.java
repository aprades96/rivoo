package com.rivoo.common.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceType, String identifier) {
        super("No %s found with identifier '%s'".formatted(resourceType, identifier));
    }
}
