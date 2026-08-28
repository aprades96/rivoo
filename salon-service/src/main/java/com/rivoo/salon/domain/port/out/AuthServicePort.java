package com.rivoo.salon.domain.port.out;

public interface AuthServicePort {

    /**
     * Registers the salon owner in Keycloak via auth-service.
     *
     * @return the Keycloak user ID
     */
    String registerOwner(String tenantId, String email, String password,
                         String firstName, String lastName, String salonName,
                         String subscriptionPlan);

    void deleteUser(String keycloakUserId);
}
