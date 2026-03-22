package com.rivoo.notification.domain.exception;

import com.rivoo.common.exception.ResourceNotFoundException;

public class NotificationNotFoundException extends ResourceNotFoundException {

    public NotificationNotFoundException(String identifier) {
        super("notification", identifier);
    }
}
