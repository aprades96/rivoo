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

    /**
     * Whether the owner has confirmed the address they registered with.
     * <p>
     * Keycloak owns that fact (it sends the mail and handles the link), and auth-service is the only
     * service holding Keycloak admin credentials, so salon-service has to ask. Any failure to reach
     * an answer throws {@code AuthServiceException} rather than returning {@code false}: "we could
     * not ask" and "the owner has not verified" are different facts, and only an explicit {@code
     * true} may make a salon publicly visible.
     */
    boolean isOwnerEmailVerified(String keycloakUserId);

    void deleteUser(String keycloakUserId);
}
