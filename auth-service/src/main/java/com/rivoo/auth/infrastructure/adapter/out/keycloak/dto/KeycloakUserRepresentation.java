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
     * <p>
     * TEMPORARY ESCAPE HATCH — {@code emailVerifiedOnCreation}: there is no SMTP server yet
     * (notification-service's {@code MailStubAdapter} only logs, and {@code rivoo-realm.json} has
     * no {@code smtpServer} block; the verification link is Keycloak's to send, not the app's), so
     * on those environments nobody could ever confirm an address and no owner could ever log in.
     * Passing {@code true} skips the requirement. Once SMTP is configured this must go back to
     * {@code false} everywhere, so the owner receives a real confirmation email.
     */
    public static KeycloakUserRepresentation forCreation(String email, String password,
                                                          String firstName, String lastName,
                                                          boolean emailVerifiedOnCreation) {
        return new KeycloakUserRepresentation(
                null, email, email, firstName, lastName,
                true, emailVerifiedOnCreation,
                List.of(new CredentialRepresentation("password", password, false)),
                emailVerifiedOnCreation ? List.of() : List.of("VERIFY_EMAIL"), null
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
