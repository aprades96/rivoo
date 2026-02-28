package com.rivoo.auth.application.dto;

public record RegisterOwnerResponse(
        String keycloakUserId,
        String email,
        String role
) {
}
