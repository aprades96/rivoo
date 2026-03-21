package com.rivoo.appointment.domain.exception;

import com.rivoo.common.exception.PlanLimitExceededException;

public class AppointmentLimitExceededException extends PlanLimitExceededException {
    public AppointmentLimitExceededException(int maxAppointments) {
        super("Monthly appointment limit of " + maxAppointments + " reached");
    }
}
