package com.rivoo.appointment.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AppointmentInternalResponse(
        String id,
        String clientName,
        String employeeName,
        String serviceName,
        BigDecimal servicePrice,
        Instant startTime,
        Instant endTime,
        String status,
        String source
) {
}
