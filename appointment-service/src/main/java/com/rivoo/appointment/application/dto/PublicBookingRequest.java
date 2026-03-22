package com.rivoo.appointment.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record PublicBookingRequest(
        @NotBlank String salonSlug,
        @NotBlank String employeeExternalId,
        @NotBlank String serviceExternalId,
        @NotBlank @Size(max = 100) String clientFirstName,
        @NotBlank @Size(max = 100) String clientLastName,
        @NotNull @Email String clientEmail,
        String clientPhone,
        @NotNull LocalDateTime requestedTime,
        String honeypot
) {
}
