package com.rivoo.admin.infrastructure.adapter.out.rest.dto;

public record TenantUsersDto(
        String keycloakUserId,
        String email,
        String firstName,
        String lastName,
        String role
) {
}
