package com.rivoo.salon.application.dto;

import jakarta.validation.constraints.Email;

public record UpdateSalonRequest(
        String name,
        @Email String email,
        String phone,
        String description,
        String addressStreet,
        String addressCity,
        String addressPostalCode,
        String timezone,
        String currency
) {
}
