package com.rivoo.staff.domain.exception;

import com.rivoo.common.exception.ResourceNotFoundException;

public class EmployeeNotFoundException extends ResourceNotFoundException {

    public EmployeeNotFoundException(String identifier) {
        super("employee", identifier);
    }
}
