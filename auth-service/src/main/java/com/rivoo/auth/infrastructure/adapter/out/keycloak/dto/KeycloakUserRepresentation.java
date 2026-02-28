package com.rivoo.auth.infrastructure.adapter.out.keycloak.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KeycloakUserRepresentation(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        Boolean enabled,
        Boolean emailVerified,
        List<Map<String, String>> credentials,
        Map<String, List<String>> attributes
) {

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
                List.of(Map.of(
                        "type", "password",
                        "value", password,
                        "temporary", "false"
                )),
                null
        );
    }
}
