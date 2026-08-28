package com.rivoo.salon.application.dto;

/**
 * The response of the ANONYMOUS {@code POST /api/v1/salons}.
 * <p>
 * A single fixed message and nothing else, by design. It used to carry {@code id}, {@code slug} and
 * {@code status}, all three of which exist only when a salon was actually created — so any of them
 * would have told an unauthenticated caller which of the two paths ran, which is exactly what this
 * endpoint must stop doing. There is also nothing left to carry: the owner cannot log in until they
 * confirm the address, so the client has no use for the salon id yet.
 */
public record RegisterSalonResponse(
        String message
) {
}
