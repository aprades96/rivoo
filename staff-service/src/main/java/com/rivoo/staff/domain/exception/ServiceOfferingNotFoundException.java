package com.rivoo.staff.domain.exception;

import com.rivoo.common.exception.ResourceNotFoundException;

public class ServiceOfferingNotFoundException extends ResourceNotFoundException {

    public ServiceOfferingNotFoundException(String identifier) {
        super("service", identifier);
    }
}
