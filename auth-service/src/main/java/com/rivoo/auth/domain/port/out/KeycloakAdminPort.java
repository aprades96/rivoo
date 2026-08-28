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
     * (temporary password) and the owner flow asks for whatever
     * {@link #pendingActionsForNewOwner()} reports. Sending an owner the employee's
     * UPDATE_PASSWORD link would force a password change nobody asked for.
     * <p>
     * An EMPTY list is a no-op: no request is made. Keycloak's {@code execute-actions-email} does
     * not merely mail the actions, it SETS them on the user, so asking it to execute nothing is
     * not harmless — it is how an already-verified owner gets VERIFY_EMAIL re-imposed and locked
     * out. Nothing pending, nothing sent.
     */
    void sendRequiredActionsEmail(String keycloakUserId, List<String> requiredActions);

    /**
     * The required actions a newly created owner still has pending — {@code VERIFY_EMAIL}, or
     * NOTHING when the owner was created already verified.
     * <p>
     * Deliberately answered by the adapter and not decided by the caller: the adapter is the sole
     * reader of {@code rivoo.keycloak.owner.email-verified-on-creation}, and it derives this from
     * the very expression that built the creation body, so the account Keycloak holds and the mail
     * Keycloak is asked to send can never describe different states.
     */
    List<String> pendingActionsForNewOwner();

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
     * Deletes a user (compensation for failed onboarding).
     */
    void deleteUser(String keycloakUserId);
}
