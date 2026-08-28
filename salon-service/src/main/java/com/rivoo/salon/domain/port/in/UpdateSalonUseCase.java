package com.rivoo.salon.domain.port.in;

import com.rivoo.salon.application.dto.SalonResponse;
import com.rivoo.salon.application.dto.UpdateSalonRequest;

public interface UpdateSalonUseCase {

    SalonResponse update(String tenantId, UpdateSalonRequest request);

    /**
     * Marks this tenant's onboarding as finished, and returns the salon either way.
     *
     * <p>Idempotent: the timestamp is written only while it is still null, so a second call —
     * a double click, two tabs, a retry — keeps the first one. Lives on this use case rather
     * than on a port of its own because a new port would change SalonController's constructor,
     * which seven tests build by hand; the cost of that has no upside here.
     */
    SalonResponse completeOnboarding(String tenantId);
}
