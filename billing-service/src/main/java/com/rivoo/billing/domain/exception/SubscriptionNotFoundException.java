package com.rivoo.billing.domain.exception;

import com.rivoo.common.exception.ResourceNotFoundException;

public class SubscriptionNotFoundException extends ResourceNotFoundException {

    public SubscriptionNotFoundException(String identifier) {
        super("subscription", identifier);
    }
}
