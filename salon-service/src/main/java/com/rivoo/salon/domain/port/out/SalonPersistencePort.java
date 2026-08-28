package com.rivoo.salon.domain.port.out;

import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SalonPersistencePort {

    Salon save(Salon salon);

    Optional<Salon> findByTenantId(String tenantId);

    Optional<Salon> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByEmail(String email);

    void deleteById(Long id);

    Page<Salon> findAll(Pageable pageable);

    List<Salon> findByStatusAndCreatedAtBefore(SalonStatus status, Instant before);

    /**
     * Promotes the salon to {@code ACTIVE}, but only while it still is {@code ONBOARDING}.
     *
     * <p>A single conditional statement rather than read-decide-write, because the decision and the
     * write have to be atomic: the owner's dashboard load is the trigger, browsers issue that
     * request more than once (two tabs, a refresh, a retried fetch), and every caller that observes
     * ONBOARDING and then writes ACTIVE would each believe it was the one that promoted the salon
     * and each send a welcome mail. Here the database arbitrates - exactly one caller can see the
     * row in ONBOARDING and change it, so exactly one gets {@code 1} back.
     *
     * <p>It also must not resurrect a salon whose status has legitimately moved on: an
     * {@code INACTIVE} or {@code SUSPENDED} salon (billing-service suspends on payment failure)
     * simply does not match, and the caller is told nothing happened.
     *
     * @return {@code 1} if this call promoted the salon, {@code 0} if it was not ONBOARDING any more
     */
    int activateIfOnboarding(String tenantId);

    /**
     * Writes {@code onboardingCompletedAt} for this tenant, but only while it is still
     * {@code null} - a single conditional statement rather than read-decide-write, so a double
     * click, two tabs, or a retried call all collapse into the same single write.
     *
     * @return {@code 1} if this call wrote the timestamp, {@code 0} if it was already set
     */
    int markOnboardingCompleted(String tenantId);
}
