package com.rivoo.appointment.application.dto;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityResponse(
        LocalDate date,
        String employeeId,
        List<AvailableSlot> slots
) {
}
