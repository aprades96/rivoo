package com.rivoo.salon.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSalonRequest(
        @Size(max = 200) String name,
        @Email String email,
        @Pattern(regexp = "^\\+?[0-9\\s\\-()]{7,20}$") String phone,
        @Size(max = 2000) String description,
        @Size(max = 300) String addressStreet,
        @Size(max = 100) String addressCity,
        @Size(max = 10) String addressPostalCode,
        String timezone,
        String currency,
        @Size(max = 500) String logoUrl,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Must be a valid hex color") String primaryColor
) {
}
