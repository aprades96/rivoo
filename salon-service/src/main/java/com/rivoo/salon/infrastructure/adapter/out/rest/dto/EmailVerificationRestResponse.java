package com.rivoo.salon.infrastructure.adapter.out.rest.dto;

/** Body of {@code GET /api/internal/auth/users/{keycloakUserId}/email-verified} in auth-service. */
public record EmailVerificationRestResponse(
        String keycloakUserId,
        Boolean emailVerified
) {
}
