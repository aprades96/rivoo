package com.rivoo.auth.domain.port.out;

import java.util.List;
import java.util.Map;

public interface KeycloakAdminPort {

    /**
     * Creates a user in Keycloak and returns the Keycloak user ID.
     */
    String createUser(String email, String password, String firstName, String lastName);

    /**
     * Creates an employee user with temporary password and required actions (UPDATE_PASSWORD, VERIFY_EMAIL).
     * Keycloak will send an email to the employee with a link to set their password.
     */
    String createEmployeeUser(String email, String password, String firstName, String lastName);

    /**
     * Sends an email asking the user to execute the given required actions.
     * <p>
     * The actions are a PARAMETER, not a constant: the employee flow asks for UPDATE_PASSWORD
     * (temporary password) and the owner flow asks for VERIFY_EMAIL only (the owner chose their
     * own password). Sending an owner the employee's UPDATE_PASSWORD link would force a password
     * change nobody asked for.
     */
    void sendRequiredActionsEmail(String keycloakUserId, List<String> requiredActions);

    /**
     * Sets user attributes (tenant_id, subscription_plan, salon_name).
     */
    void setUserAttributes(String keycloakUserId, Map<String, List<String>> attributes);

    /**
     * Assigns a realm-level role to the user.
     */
    void assignRealmRole(String keycloakUserId, String roleName);

    /**
     * Searches for users by attribute (e.g., tenant_id).
     */
    List<String> searchUserIdsByAttribute(String attributeName, String attributeValue);

    /**
     * Enables or disables a user.
     */
    void setUserEnabled(String keycloakUserId, boolean enabled);

    /**
     * Updates a single attribute for a user.
     */
    void updateUserAttribute(String keycloakUserId, String key, String value);

    /**
     * Whether Keycloak has this user's email address confirmed.
     * <p>
     * Read-only projection of the single Keycloak field that decides it ({@code emailVerified}).
     * Keycloak owns the verification flow end to end - it sends the mail, it handles the link, it
     * flips the flag - so this is the only way another service can learn the outcome without
     * deploying an event-listener extension into Keycloak itself.
     */
    boolean isEmailVerified(String keycloakUserId);

    /**
     * Deletes a user (compensation for failed onboarding).
     */
    void deleteUser(String keycloakUserId);
}
