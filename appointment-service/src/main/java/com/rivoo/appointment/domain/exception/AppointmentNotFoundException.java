package com.rivoo.appointment.domain.exception;

import com.rivoo.common.exception.ResourceNotFoundException;

public class AppointmentNotFoundException extends ResourceNotFoundException {
    public AppointmentNotFoundException(String identifier) {
        super("appointment", identifier);
    }

    /**
     * Authenticated-only: the single throw site is AppointmentService#findOrThrow, reached from
     * GET /api/v1/appointments/{id}, PUT /{id}/status and PUT /{id}/cancel, all
     * hasAnyRole('SALON_OWNER','EMPLOYEE'). The anonymous booking flow never looks an
     * appointment up by id, so the identifier in the message is always the caller's own.
     */
    @Override
    public String clientSafeDetail() {
        return getMessage();
    }
}
