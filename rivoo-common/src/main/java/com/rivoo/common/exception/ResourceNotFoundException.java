package com.rivoo.common.exception;

import org.springframework.http.HttpStatus;

/**
 * No {@code clientSafeDetail()} override, deliberately: it is a shared base class, so publishing
 * here would be inherited by every future subtype. appointment-service's
 * {@code SalonNotFoundException} extends it and is raised on the two anonymous public flows,
 * where the response must stay identical for a slug that does not exist and a slug that is merely
 * suspended. Subtypes reachable only from authenticated endpoints opt in individually.
 */
public class ResourceNotFoundException extends RivooException {

    public ResourceNotFoundException(String message) {
        super(message, "resource-not-found", "Resource Not Found", HttpStatus.NOT_FOUND);
    }

    public ResourceNotFoundException(String resourceType, String identifier) {
        super("No %s found with identifier '%s'".formatted(resourceType, identifier),
                "resource-not-found", "Resource Not Found", HttpStatus.NOT_FOUND);
    }
}
