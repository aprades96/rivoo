package com.rivoo.appointment.domain.port.in;

import com.rivoo.appointment.application.dto.AppointmentResponse;
import com.rivoo.appointment.application.dto.CreateAppointmentRequest;

public interface CreateAppointmentUseCase {
    AppointmentResponse create(String tenantId, CreateAppointmentRequest request);
}
