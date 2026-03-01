package com.rivoo.salon.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterSalonRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "^\\+?[0-9\\s\\-()]{7,20}$") String phone,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 300) String addressStreet,
        @Size(max = 100) String addressCity,
        @NotBlank @Size(max = 10) String addressPostalCode,
        @NotBlank String ownerFirstName,
        @NotBlank String ownerLastName,
        @NotBlank @Size(min = 8) String ownerPassword
) {
}
