package com.rivoo.appointment.domain.port.in;

import com.rivoo.appointment.application.dto.AppointmentResponse;
import com.rivoo.appointment.application.dto.CancelAppointmentRequest;

public interface CancelAppointmentUseCase {
    AppointmentResponse cancel(String externalId, CancelAppointmentRequest request);
}
