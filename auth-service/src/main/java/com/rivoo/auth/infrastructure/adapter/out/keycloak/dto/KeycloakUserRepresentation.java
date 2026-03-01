package com.rivoo.auth.infrastructure.adapter.out.keycloak.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakUserRepresentation(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        Boolean enabled,
        Boolean emailVerified,
        List<CredentialRepresentation> credentials,
        Map<String, List<String>> attributes
) {

    public record CredentialRepresentation(
            String type,
            String value,
            Boolean temporary
    ) {}

    public static KeycloakUserRepresentation forCreation(String email, String password,
                                                          String firstName, String lastName) {
        return new KeycloakUserRepresentation(
                null,
                email,
                email,
                firstName,
                lastName,
                true,
                true,
                List.of(new CredentialRepresentation("password", password, false)),
                null
        );
    }
}
