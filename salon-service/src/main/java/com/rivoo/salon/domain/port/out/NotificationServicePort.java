package com.rivoo.salon.domain.port.out;

public interface NotificationServicePort {

    void sendWelcomeEmail(String tenantId, String recipientEmail, String salonName);

    /**
     * Tells the owner of an ALREADY REGISTERED address that someone just tried to register with it.
     * <p>
     * This is the whole point of the anonymous registration endpoint answering identically for a
     * free address and a taken one: the difference does not go in the HTTP response (where an
     * attacker reads it), it goes in the inbox (where only the address owner reads it).
     * <p>
     * No tenantId parameter on purpose. The caller must NOT resolve which tenant owns the address:
     * one of the two cases that reach here (an address known to Keycloak but with no salon row) has
     * no tenant at all, and looking one up would only add a branch that could behave differently
     * between the two.
     */
    void sendExistingAccountRegistrationAttempt(String recipientEmail);
}
