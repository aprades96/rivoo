package com.rivoo.appointment.application.dto;

import java.time.Instant;

public record PublicBookingResponse(
        String appointmentId,
        String salonName,
        String employeeName,
        String serviceName,
        Instant startTime,
        Instant endTime,
        String status
) {
}
