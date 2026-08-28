package com.rivoo.notification.domain.exception;

import com.rivoo.common.exception.ResourceNotFoundException;

public class NotificationNotFoundException extends ResourceNotFoundException {

    public NotificationNotFoundException(String identifier) {
        super("notification", identifier);
    }

    /**
     * Authenticated-only by construction: notification-service exposes only /api/internal/**
     * (PSK-gated by InternalEndpointFilter) and inherits anyRequest().authenticated() for the
     * rest, so it has no anonymous surface. Note this exception currently has no throw site.
     */
    @Override
    public String clientSafeDetail() {
        return getMessage();
    }
}
