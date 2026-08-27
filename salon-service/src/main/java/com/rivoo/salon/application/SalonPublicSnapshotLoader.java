package com.rivoo.salon.application;

import com.rivoo.salon.domain.exception.SalonNotFoundException;
import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonBusinessHours;
import com.rivoo.salon.domain.model.SalonStatus;
import com.rivoo.salon.domain.port.out.BusinessHoursPersistencePort;
import com.rivoo.salon.domain.port.out.SalonPersistencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Isolates the transactional (JDBC-bound) read step of the public salon
 * aggregate from the remote staff-service calls that must happen after it.
 * <p>
 * This MUST be a separate Spring bean, not a private/self-invoked method on
 * {@link SalonService}: Spring's {@code @Transactional} is implemented via a
 * proxy, and a call from one method to another on the same instance bypasses
 * that proxy entirely (self-invocation), so the annotation would silently do
 * nothing. Going through a distinct bean guarantees the proxy — and therefore
 * the transaction boundary — is actually applied.
 */
@Service
@RequiredArgsConstructor
class SalonPublicSnapshotLoader {

    private final SalonPersistencePort salonPersistencePort;
    private final BusinessHoursPersistencePort businessHoursPersistencePort;

    @Transactional(readOnly = true)
    public SalonPublicSnapshot loadActiveSalon(String slug) {
        // A non-ACTIVE salon (ONBOARDING, INACTIVE, SUSPENDED, FAILED) must be
        // indistinguishable from a non-existent one to an anonymous visitor:
        // filtering here (instead of checking status after the lookup) keeps
        // both cases resolving to the same 404, without leaking salon state.
        Salon salon = salonPersistencePort.findBySlug(slug)
                .filter(s -> s.getStatus() == SalonStatus.ACTIVE)
                .orElseThrow(() -> new SalonNotFoundException(slug));

        List<SalonBusinessHours> businessHours = businessHoursPersistencePort.findBySalonId(salon.getId());

        return new SalonPublicSnapshot(salon, businessHours);
    }
}
