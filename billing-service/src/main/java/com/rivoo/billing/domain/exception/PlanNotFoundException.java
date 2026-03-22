package com.rivoo.billing.domain.exception;

import com.rivoo.common.exception.ResourceNotFoundException;

public class PlanNotFoundException extends ResourceNotFoundException {

    public PlanNotFoundException(String identifier) {
        super("plan", identifier);
    }
}
