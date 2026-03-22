package com.rivoo.client.application.dto;

import java.time.Instant;

public record ClientAppointmentDto(
        String id,
        String serviceName,
        String employeeName,
        Instant startTime,
        Instant endTime,
        String status
) {
}
