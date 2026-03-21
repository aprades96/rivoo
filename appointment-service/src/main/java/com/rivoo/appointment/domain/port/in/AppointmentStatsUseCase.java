package com.rivoo.appointment.domain.port.in;

import com.rivoo.appointment.application.dto.AppointmentStatsResponse;

public interface AppointmentStatsUseCase {
    AppointmentStatsResponse getStats(String tenantId);
}
