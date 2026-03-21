package com.rivoo.appointment.domain.port.in;

import com.rivoo.appointment.application.dto.AppointmentResponse;

public interface UpdateAppointmentStatusUseCase {
    AppointmentResponse updateStatus(String externalId, String newStatus);
}
