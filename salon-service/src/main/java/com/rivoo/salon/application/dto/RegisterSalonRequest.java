package com.rivoo.salon.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterSalonRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank String phone,
        String description,
        @NotBlank String addressStreet,
        String addressCity,
        @NotBlank String addressPostalCode,
        @NotBlank String ownerFirstName,
        @NotBlank String ownerLastName,
        @NotBlank @Size(min = 8) String ownerPassword
) {
}
