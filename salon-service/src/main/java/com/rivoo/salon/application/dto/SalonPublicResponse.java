package com.rivoo.salon.application.dto;

public record SalonPublicResponse(
        String name,
        String slug,
        String phone,
        String description,
        String addressStreet,
        String addressCity,
        String addressPostalCode
) {
}
