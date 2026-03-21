package com.rivoo.appointment.application.dto;

import jakarta.validation.constraints.Size;

public record CancelAppointmentRequest(
        @Size(max = 500) String reason,
        String cancelledBy
) {
}
