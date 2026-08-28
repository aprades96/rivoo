package com.rivoo.staff.domain.exception;

import com.rivoo.common.exception.ResourceNotFoundException;

public class ServiceOfferingNotFoundException extends ResourceNotFoundException {

    public ServiceOfferingNotFoundException(String identifier) {
        super("service", identifier);
    }

    /**
     * Authenticated-only by construction: see EmployeeNotFoundException. staff-service has no
     * anonymous surface; every throw site is in EmployeeService / ServiceOfferingService.
     */
    @Override
    public String clientSafeDetail() {
        return getMessage();
    }
}
