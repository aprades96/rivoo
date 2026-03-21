package com.rivoo.appointment.domain.exception;

import com.rivoo.common.exception.BusinessValidationException;

public class AppointmentConflictException extends BusinessValidationException {
    public AppointmentConflictException(String employeeName, String timeRange) {
        super("Employee '" + employeeName + "' already has an appointment during " + timeRange);
    }
}
