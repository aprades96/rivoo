package com.rivoo.appointment.domain.exception;

import com.rivoo.common.exception.ResourceNotFoundException;

public class AppointmentNotFoundException extends ResourceNotFoundException {
    public AppointmentNotFoundException(String identifier) {
        super("appointment", identifier);
    }
}
