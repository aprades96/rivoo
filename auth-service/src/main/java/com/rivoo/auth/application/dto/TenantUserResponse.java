package com.rivoo.auth.application.dto;

public record TenantUserResponse(
        String keycloakUserId,
        String tenantId,
        String role,
        boolean active
) {
}
