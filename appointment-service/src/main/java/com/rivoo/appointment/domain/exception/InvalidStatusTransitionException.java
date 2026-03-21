package com.rivoo.appointment.domain.exception;

import com.rivoo.common.exception.BusinessValidationException;

public class InvalidStatusTransitionException extends BusinessValidationException {
    public InvalidStatusTransitionException(String currentStatus, String targetStatus) {
        super("Cannot transition from " + currentStatus + " to " + targetStatus);
    }
}
