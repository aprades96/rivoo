package com.rivoo.auth.application.dto;

/**
 * Answer of {@code GET /api/internal/auth/users/{keycloakUserId}/email-verified}.
 * <p>
 * Internal (PSK) endpoint, so it may echo the id it was asked about; it deliberately carries
 * nothing else about the user - no address, no name - because the caller only needs the flag.
 */
public record EmailVerificationResponse(
        String keycloakUserId,
        boolean emailVerified
) {
}
