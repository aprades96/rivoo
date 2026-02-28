package com.rivoo.auth.application.dto;

public record RegisterEmployeeResponse(
        String keycloakUserId,
        String email,
        String role
) {
}
