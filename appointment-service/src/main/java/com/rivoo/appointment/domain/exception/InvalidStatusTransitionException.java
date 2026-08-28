package com.rivoo.appointment.domain.exception;

import com.rivoo.common.exception.BusinessValidationException;

public class InvalidStatusTransitionException extends BusinessValidationException {
    public InvalidStatusTransitionException(String currentStatus, String targetStatus) {
        super("Cannot transition from " + currentStatus + " to " + targetStatus);
    }

    /**
     * Authenticated-only: thrown by AppointmentService#updateStatus and #cancel, both
     * hasAnyRole('SALON_OWNER','EMPLOYEE'). Knowing which transition was refused is exactly
     * what the salon owner needs, and the message carries no identifier.
     */
    @Override
    public String clientSafeDetail() {
        return getMessage();
    }
}
