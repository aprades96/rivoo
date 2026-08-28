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
 * refused. It does NOT make this endpoint safe against account enumeration, and no comment here
 * should claim otherwise. The saga pre-checks the email and answers 409 with
 * "Email already in use: &lt;email&gt;" (see {@code EmailAlreadyInUseException} and
 * {@code SalonExceptionHandler#handleEmailAlreadyInUse}) before any dependency is called, so an
 * anonymous caller learns whether an address is registered without ever reaching a 422. That
 * oracle is a known, deliberate product trade-off pending a separate decision — it is simply not
 * something this constant closes.
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
