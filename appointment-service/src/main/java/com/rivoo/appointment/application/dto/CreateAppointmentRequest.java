package com.rivoo.appointment.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateAppointmentRequest(
        @NotBlank String employeeId,
        @NotBlank String serviceId,
        String clientId,
        @NotBlank @Size(max = 200) String clientName,
        @Pattern(regexp = "^\\+?[0-9\\s\\-()]{7,20}$") String clientPhone,
        @Email String clientEmail,
        @NotNull LocalDateTime startTime,
        String source,
        @Size(max = 2000) String notes
) {
}
