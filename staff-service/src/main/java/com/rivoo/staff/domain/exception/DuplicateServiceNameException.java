package com.rivoo.staff.domain.exception;

import com.rivoo.common.exception.BusinessValidationException;

public class DuplicateServiceNameException extends BusinessValidationException {

    public DuplicateServiceNameException(String name) {
        super("A service with name '" + name + "' already exists");
    }

    /**
     * Authenticated-only by construction: staff-service has no anonymous surface. Both throw
     * sites are in ServiceOfferingService, behind hasRole('SALON_OWNER'), so the service name
     * echoed back is one the caller just submitted for their own tenant.
     */
    @Override
    public String clientSafeDetail() {
        return getMessage();
    }
}
