package com.rivoo.salon.domain.port.in;

import com.rivoo.salon.application.dto.SalonPublicResponse;
import com.rivoo.salon.application.dto.SalonResponse;

public interface GetSalonUseCase {

    /**
     * The owner's own salon, and the moment a salon registered as {@code ONBOARDING} becomes
     * publicly visible.
     * <p>
     * Registration through the anonymous {@code POST /api/v1/salons} deliberately leaves the salon
     * out of every public surface, because at that point nobody has proved they control the address
     * that was submitted. This call is the proof: Keycloak will not issue a token for a user with a
     * pending {@code VERIFY_EMAIL} required action, so the request having arrived here at all means
     * the owner completed it. Nothing has to go and ask.
     *
     * @param ownerEmailVerifiedClaim the caller's {@code email_verified} claim, {@code null} when
     *        the realm does not map it. Only an explicit {@code FALSE} withholds the promotion — see
     *        the implementation for why an absent claim must not.
     */
    SalonResponse getByTenantId(String tenantId, Boolean ownerEmailVerifiedClaim);

    SalonResponse getBySlug(String slug);

    SalonPublicResponse getPublicBySlug(String slug);
}
