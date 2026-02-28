package com.rivoo.salon.infrastructure.adapter.out.rest.dto;

public record RegisterOwnerResponse(
        String keycloakUserId,
        String email,
        String role
) {
}
