package com.rivoo.appointment.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AppointmentResponse(
        String id,
        String clientId,
        String clientName,
        String clientPhone,
        String clientEmail,
        String employeeId,
        String employeeName,
        String serviceId,
        String serviceName,
        BigDecimal servicePrice,
        int serviceDurationMinutes,
        Instant startTime,
        Instant endTime,
        String status,
        String cancellationReason,
        String cancelledBy,
        String source,
        String notes,
        boolean reminderSent,
        Instant createdAt,
        Instant updatedAt
) {
}
