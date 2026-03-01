package com.rivoo.staff.domain.exception;

import com.rivoo.common.exception.BusinessValidationException;

public class DuplicateServiceNameException extends BusinessValidationException {

    public DuplicateServiceNameException(String name) {
        super("A service with name '" + name + "' already exists");
    }
}
