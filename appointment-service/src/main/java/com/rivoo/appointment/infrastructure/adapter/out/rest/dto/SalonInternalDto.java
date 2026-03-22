package com.rivoo.appointment.infrastructure.adapter.out.rest.dto;

public record SalonInternalDto(
        String id,
        String name,
        String slug,
        String status
) {
}
