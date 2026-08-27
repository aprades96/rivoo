package com.rivoo.appointment.domain.exception;

import com.rivoo.common.exception.ResourceNotFoundException;

/**
 * Thrown by the anonymous public flows (public availability, public booking)
 * whenever the resolved salon must be treated as unreachable to the caller.
 * <p>
 * This is thrown for two distinct upstream situations — a slug that does not
 * exist in salon-service at all, and a slug that exists but resolves to a
 * salon that is not {@code ACTIVE} — and it is deliberately the SAME
 * exception, constructed the same way, in both cases. An anonymous caller
 * must not be able to tell a suspended/onboarding business apart from one
 * that was never registered: see {@code SalonServiceAdapter#getSalonBySlug}
 * for the "does not exist" case and the two callers of
 * {@code SalonServicePort#getSalonBySlug} (in {@code AvailabilityService} and
 * {@code AppointmentService}) for the "not ACTIVE" case. Matches the pattern
 * already used for the same problem in salon-service's own public endpoint
 * (see {@code SalonPublicSnapshotLoader}).
 */
public class SalonNotFoundException extends ResourceNotFoundException {

    public SalonNotFoundException(String slug) {
        super("salon", slug);
    }
}
