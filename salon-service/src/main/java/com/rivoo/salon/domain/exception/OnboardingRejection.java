package com.rivoo.salon.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * The single Problem Details identity used whenever a dependency of the salon-registration saga
 * answered correctly and refused the request (any 4xx) — as opposed to being broken, which stays
 * a 502 with the dependency's own error type.
 * <p>
 * Deliberately one shared identity for every dependency instead of one per service: {@code POST
 * /api/v1/salons} is anonymous, so the {@code type} and {@code title} it publishes are read by
 * unauthenticated callers. A per-dependency type ({@code auth-...} / {@code billing-...}) would
 * tell that caller which internal service refused, which is exactly the internal topology this
 * endpoint must not describe. The operator gets that distinction from the logs instead.
 * <p>
 * What this mapping achieves is precisely that and nothing more: it hides WHICH dependency
 * refused. Nothing about account enumeration lives in this constant, and no future change to it
 * should be read as maintaining or breaking that property.
 * <p>
 * <b>And account enumeration is NOT closed elsewhere either.</b> Exactly one channel is:
 * <ul>
 *   <li>CLOSED — the RESPONSE. 202 with one fixed body, byte-identical whether the address was free,
 *       already had a salon, or was already a Keycloak user, the difference going to the address
 *       owner by mail (see {@code OnboardingSagaService#register} and
 *       {@code EmailAlreadyInUseException}).</li>
 *   <li>OPEN — SLUG ALLOCATION. Registering under {@code ONBOARDING} instead of {@code ACTIVE} stops
 *       the new salon appearing on the public page, but the ROW still exists and still holds the
 *       slug it derived from the attacker's {@code name}. Two anonymous registrations with the same
 *       {@code name} — the victim's address, then a disposable one the attacker controls — allocate
 *       {@code probe-x} then {@code probe-x-2} if the first created a row, and {@code probe-x} for
 *       the second one if it did not. The attacker reads which they got from their own salon.</li>
 *   <li>OPEN — TIMING, in three classes of round-trip count, discriminable from a single sample.
 *       Closing it needs asynchronous registration, which nothing here does.</li>
 * </ul>
 * Both open channels are described in full in the module CLAUDE.md.
 * <p>
 * One consequence worth stating: a 422 carrying this identity now means a dependency refused for a
 * reason that is NOT "the address exists" (a password the Keycloak policy rejects, for instance),
 * because the 409 case is intercepted before it can reach here.
 * <p>
 * Not in {@code com.rivoo.common.web.RivooErrorTypes}: per that class's own javadoc, only values
 * a DIFFERENT service parses belong there, and no consumer branches on this one.
 */
final class OnboardingRejection {

    static final String ERROR_TYPE = "salon-registration-rejected";
    static final String ERROR_TITLE = "Salon Registration Rejected";
    static final HttpStatus HTTP_STATUS = HttpStatus.UNPROCESSABLE_ENTITY;

    private OnboardingRejection() {
    }
}
