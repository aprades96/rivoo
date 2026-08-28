package com.rivoo.auth.domain.port.in;

/**
 * Answers "has this user proved the address is theirs?" for the services that gate behaviour on it.
 * <p>
 * salon-service is the caller: a salon stays out of the public directory until its owner's address
 * is confirmed, and Keycloak is the only place that fact exists.
 */
public interface CheckEmailVerificationUseCase {

    boolean isEmailVerified(String keycloakUserId);
}
