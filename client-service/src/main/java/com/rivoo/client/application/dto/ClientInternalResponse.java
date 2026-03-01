package com.rivoo.client.application.dto;

public record ClientInternalResponse(
        String id,
        String firstName,
        String lastName,
        String email,
        String phone,
        boolean active
) {
}
