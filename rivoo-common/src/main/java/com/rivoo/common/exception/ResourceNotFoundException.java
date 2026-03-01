package com.rivoo.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends RivooException {

    public ResourceNotFoundException(String message) {
        super(message, "resource-not-found", "Resource Not Found", HttpStatus.NOT_FOUND);
    }

    public ResourceNotFoundException(String resourceType, String identifier) {
        super("No %s found with identifier '%s'".formatted(resourceType, identifier),
                "resource-not-found", "Resource Not Found", HttpStatus.NOT_FOUND);
    }
}
