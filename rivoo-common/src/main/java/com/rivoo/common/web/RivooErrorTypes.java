package com.rivoo.common.web;

/**
 * {@code ProblemDetail.type} values shared across service boundaries — i.e. values that one
 * service's producer sets and a DIFFERENT service's consumer parses to decide behavior (as
 * opposed to a value only ever inspected by the same service that raised it, which does not
 * need a shared constant here).
 * <p>
 * A value in this class MUST be treated as part of the public inter-service contract: renaming
 * it is a breaking change for every consumer, not a local rename.
 */
public final class RivooErrorTypes {

    /**
     * The value salon-service's {@code SalonExceptionHandler} sets on the {@code ProblemDetail}
     * body of a genuine "no salon for this slug" 404. appointment-service's
     * {@code SalonServiceAdapter} compares an upstream 404's {@code type} against this same
     * constant to decide whether it means "the slug does not exist" (as opposed to a
     * misconfigured URL, a renamed route, or an unrelated 404) — this is the marker the
     * reserva-publica anti-enumeration property depends on.
     */
    public static final String SALON_NOT_FOUND = "https://rivoo.com/errors/salon-not-found";

    private RivooErrorTypes() {
    }
}
