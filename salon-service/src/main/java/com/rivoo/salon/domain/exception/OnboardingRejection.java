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
