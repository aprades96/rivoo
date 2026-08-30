package com.rivoo.client.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

// price is transported from appointment-service's servicePrice (D38, §1.11.6 point 5):
// the data already existed there, this DTO just did not carry it yet.
public record ClientAppointmentDto(
        String id,
        String serviceName,
        String employeeName,
        Instant startTime,
        Instant endTime,
        BigDecimal price,
        String status
) {
}
