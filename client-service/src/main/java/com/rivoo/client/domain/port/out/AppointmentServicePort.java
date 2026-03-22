package com.rivoo.client.domain.port.out;

import com.rivoo.client.application.dto.ClientAppointmentDto;

import java.util.List;

public interface AppointmentServicePort {
    List<ClientAppointmentDto> getClientAppointments(String clientExternalId, String tenantId);
}
