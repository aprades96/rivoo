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
 * should be read as maintaining or breaking that property. That work is done SEPARATELY and
 * elsewhere, in two halves and with one hole left open:
 * <ul>
 *   <li>the RESPONSE is uniform — 202 with one fixed body whether or not the address already has an
 *       account, the difference going to the address owner by mail (see
 *       {@code OnboardingSagaService#register} and {@code EmailAlreadyInUseException});</li>
 *   <li>the SIDE EFFECT is uniform — registration no longer publishes the salon, so the follow-up
 *       {@code GET /api/v1/salons/public/{slug}} that used to answer 200 for a free address and 404
 *       for a taken one now answers 404 either way until the owner verifies their address (see
 *       {@code OwnerVerificationActivationService});</li>
 *   <li>the TIMING is NOT uniform and is not addressed: the free path does DB writes plus three
 *       synchronous inter-service calls, the taken path one query and one notification POST. That is
 *       a difference in round-trip count, discriminable from a single sample. Closing it needs
 *       asynchronous registration, which nothing here does.</li>
 * </ul>
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
