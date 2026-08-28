package com.rivoo.staff.domain.exception;

import com.rivoo.common.exception.ResourceNotFoundException;

public class EmployeeNotFoundException extends ResourceNotFoundException {

    public EmployeeNotFoundException(String identifier) {
        super("employee", identifier);
    }

    /**
     * Authenticated-only by construction: staff-service declares no security config of its own,
     * so it inherits rivoo-common's SecurityConfig (anyRequest().authenticated()) and has no
     * anonymous surface. Its /api/internal/** routes - the ones the anonymous public booking
     * reaches indirectly - are PSK-gated, and appointment-service's StaffServiceAdapter never
     * forwards this body to its own caller (it wraps failures in a plain RuntimeException).
     */
    @Override
    public String clientSafeDetail() {
        return getMessage();
    }
}
