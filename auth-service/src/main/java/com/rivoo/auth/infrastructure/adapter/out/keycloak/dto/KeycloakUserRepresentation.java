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
        List<String> requiredActions,
        Map<String, List<String>> attributes
) {

    public record CredentialRepresentation(
            String type,
            String value,
            Boolean temporary
    ) {}

    /**
     * The salon OWNER, created from the anonymous {@code POST /api/v1/salons}.
     * <p>
     * {@code emailVerified=false} plus the {@code VERIFY_EMAIL} required action: nobody has proved
     * they control this address at this point, so Keycloak must not be told otherwise. Declaring it
     * verified (what this factory used to do) let anyone register a salon with a stranger's address
     * and silently redirect that stranger's password-recovery, billing and reminder mail.
     * <p>
     * {@code UPDATE_PASSWORD} is deliberately NOT required here, unlike
     * {@link #forEmployeeCreation}: the owner chose this password themselves during registration
     * ({@code temporary=false}), so there is nothing for them to update.
     * <p>
     * Consequence, intended: Keycloak refuses login until the address is confirmed.
     */
    public static KeycloakUserRepresentation forCreation(String email, String password,
                                                          String firstName, String lastName) {
        return new KeycloakUserRepresentation(
                null, email, email, firstName, lastName,
                true, false,
                List.of(new CredentialRepresentation("password", password, false)),
                List.of("VERIFY_EMAIL"), null
        );
    }

    public static KeycloakUserRepresentation forEmployeeCreation(String email, String password,
                                                                   String firstName, String lastName) {
        return new KeycloakUserRepresentation(
                null, email, email, firstName, lastName,
                true, false,
                List.of(new CredentialRepresentation("password", password, true)),
                List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"),
                null
        );
    }
}
