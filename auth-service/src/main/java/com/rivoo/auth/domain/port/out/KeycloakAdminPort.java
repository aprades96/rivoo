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
     * Sends an email to the user to execute required actions (e.g., UPDATE_PASSWORD, VERIFY_EMAIL).
     */
    void sendRequiredActionsEmail(String keycloakUserId);

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
