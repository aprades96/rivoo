package com.rivoo.common.exception;

import org.springframework.http.HttpStatus;

/**
 * No {@code clientSafeDetail()} override, deliberately, for two independent reasons.
 * <p>
 * First, it is thrown directly on an ANONYMOUS path: {@code AppointmentService#book}
 * ({@code POST /api/v1/appointments/book}) raises it for the booking-window and
 * "employee/service is not active" checks.
 * <p>
 * Second and more important, it is a shared base class. Publishing here would be inherited by
 * every future subtype, which is precisely the fail-open default this class hierarchy moved away
 * from — {@code AppointmentConflictException} extended it and leaked an employee's full name to
 * unauthenticated callers for exactly that reason. Subtypes opt in individually.
 */
public class BusinessValidationException extends RivooException {

    public BusinessValidationException(String message) {
        super(message, "business-validation", "Business Validation Failed", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
