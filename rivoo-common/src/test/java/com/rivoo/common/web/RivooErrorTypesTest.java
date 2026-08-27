package com.rivoo.common.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the literal value of every constant in {@link RivooErrorTypes}.
 * <p>
 * This is deliberately NOT a tautology (asserting a constant equals itself proves nothing).
 * These strings are published as {@code ProblemDetail.type} in HTTP responses that reach
 * anonymous callers (see the reserva-publica flow) and are also parsed by a DIFFERENT
 * service's consumer code (e.g. appointment-service's {@code SalonServiceAdapter} matching
 * on {@code SALON_NOT_FOUND} per RivooErrorTypes' own class javadoc). Centralizing the
 * constant in rivoo-common made cross-service divergence impossible, but it also means no
 * test elsewhere fixes the literal anymore: a producer and its consumer(s) now always agree
 * with each other by construction, so a silent rename here would compile cleanly on both
 * sides and only break at runtime for whatever external caller still expects the old URI.
 * This test exists to force a human to stop and think "this is a published contract, not an
 * internal detail" before changing the literal.
 */
class RivooErrorTypesTest {

    @Test
    void salonNotFound_isThePublishedContractValue() {
        assertThat(RivooErrorTypes.SALON_NOT_FOUND)
                .as("published Problem Details 'type' URI — changing it is a breaking change for every consumer")
                .isEqualTo("https://rivoo.com/errors/salon-not-found");
    }
}
