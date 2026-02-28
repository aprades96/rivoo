package com.rivoo.auth.infrastructure.adapter.out.keycloak.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakRoleRepresentation(
        String id,
        String name
) {
}
