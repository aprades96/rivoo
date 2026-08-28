package com.rivoo.salon.domain.exception;

import com.rivoo.common.exception.RivooException;
import org.springframework.http.HttpStatus;

/**
 * INTERNAL signal: the address already has an account. It never reaches an HTTP response.
 * <p>
 * It used to be exactly the opposite — thrown by the saga and rendered by
 * {@code SalonExceptionHandler} as a 409 whose {@code detail} named the address, on an ANONYMOUS
 * endpoint. That made {@code POST /api/v1/salons} an account-enumeration oracle: probe an address,
 * read the status, learn whether it belongs to a Rivoo owner. The product decision that javadoc was
 * waiting for has landed — hide it — and it is now hidden at no UX cost, because registration ends
 * in "check your email" either way and the address owner is told by mail instead.
 * <p>
 * What it is FOR now: {@code AuthServiceAdapter} throws it when auth-service answers 409 (the
 * address is a Keycloak user even though no salon row carries it — an employee's address, or an
 * orphan from a compensated onboarding), and {@code OnboardingSagaService} catches it to reach the
 * same silent outcome as its own {@code existsByEmail} pre-check. Without that, the Keycloak-only
 * case would still answer 422 while a free address answered 202, and the oracle would survive in
 * the exact population this change exists to protect.
 * <p>
 * No {@code clientSafeDetail()} override any more: the default publishes nothing, so should this
 * ever escape the saga it degrades to a generic 409 rather than to a message naming the address.
 * That escape is a bug, not a fallback — {@code SalonRegistrationEnumerationTest} pins that both
 * paths answer 202.
 */
public class EmailAlreadyInUseException extends RivooException {

    public EmailAlreadyInUseException(String email) {
        super("Email already in use: " + email, "email-already-in-use", "Email Already In Use", HttpStatus.CONFLICT);
    }
}
