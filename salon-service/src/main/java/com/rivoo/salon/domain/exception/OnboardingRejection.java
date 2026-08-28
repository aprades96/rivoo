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
 * refused. Account enumeration is closed SEPARATELY and elsewhere — the saga now answers 202 with
 * one fixed body whether or not the address already has an account, and tells the address owner by
 * mail instead (see {@code OnboardingSagaService#register} and {@code EmailAlreadyInUseException}).
 * Nothing about that lives in this constant, and no future change to it should be read as
 * maintaining that property.
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
