package com.rivoo.salon.infrastructure.adapter.out.persistence.adapter;

import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonStatus;
import com.rivoo.salon.domain.model.SubscriptionPlan;
import com.rivoo.salon.infrastructure.adapter.out.persistence.entity.SalonJpaEntity;
import com.rivoo.salon.infrastructure.adapter.out.persistence.repository.SalonJpaRepository;
import com.rivoo.salon.infrastructure.config.SalonSchedulingConfig;
import com.rivoo.salon.infrastructure.mapper.SalonPersistenceMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link SalonPersistenceAdapter#save(Salon)} against a genuine MySQL connection to prove
 * that {@code SalonJpaEntity.onboardingCompletedAt}'s {@code updatable = false} (see that field's
 * javadoc) is actually enforced by the database, not merely asserted in a comment. Removing that
 * attribute leaves the wider 92-test suite green, because nothing else in this module ever exercises
 * a real {@code merge()} of a fully-populated, previously-detached {@link Salon} whose
 * {@code onboardingCompletedAt} is stale relative to what a concurrent compare-and-set already
 * committed - which is exactly what {@link SalonPersistenceAdapter#save} does, and exactly what
 * {@code SalonService#updateStatus} calls it with.
 * <p>
 * <b>The race being reproduced.</b> {@code SalonService#updateStatus} (invoked by admin-service, via
 * {@code PUT /api/internal/salons/{tenantId}/status}) reads the whole {@link Salon} aggregate,
 * changes only {@code status}, and calls {@code save} - a read-decide-write over every column, not a
 * targeted update. If between that read and that write {@code SalonService#completeOnboarding}'s
 * compare-and-set lands, the stale aggregate still holds {@code onboardingCompletedAt == null} in
 * memory; without {@code updatable = false}, {@code save}'s {@code merge()} would put that stale
 * {@code null} back over the timestamp the compare-and-set had just committed, silently un-completing
 * onboarding. This test does not attempt to reproduce the race's timing (that would be flaky); it
 * pins the invariant that actually closes it - a full-aggregate {@code save} can never move this
 * column, no matter what value the aggregate carries.
 * <p>
 * This class intentionally mirrors {@code SalonJpaRepositoryOnboardingCompletionIntegrationTest}
 * (same package one level up) in every infrastructure choice - {@code @SpringBootTest} over
 * {@code @DataJpaTest} (unavailable in this Spring Boot 4.0.3 resolution, see that class's javadoc),
 * the {@code local} Spring profile against the real local MySQL instead of Testcontainers (Docker is
 * not available in this environment), class-level {@code @Transactional} so every row this test
 * creates is rolled back and the schema's 15 pre-existing salons are never read or written, and
 * mocking {@link SalonSchedulingConfig} as a safety net against its cleanup job (irrelevant here since
 * every row this test seeds is {@code ACTIVE}/{@code INACTIVE}, never {@code ONBOARDING}). See that
 * class's javadoc for the full rationale behind each of those choices; it is not repeated here.
 * <p>
 * <b>Why an explicit {@link EntityManager#flush()} is required here but not in the sibling class.</b>
 * {@code SalonJpaRepository}'s bulk JPQL updates are annotated {@code @Modifying(flushAutomatically =
 * true)}, so they reach the database on their own. {@link SalonPersistenceAdapter#save} goes through
 * plain {@code JpaRepository.save()}, which only queues the {@code merge()} - without a manual flush
 * here, the pending change would still be sitting unflushed when {@link EntityManager#clear()} runs
 * right after it, and clearing an unflushed persistence context is a no-op for that change: the test
 * would then trivially pass regardless of whether {@code updatable = false} is present, because
 * nothing was ever sent to MySQL to prove or disprove the invariant.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
@Tag("integration")
class SalonPersistenceAdapterOnboardingTimestampProtectionIntegrationTest {

    @Autowired
    private SalonPersistenceAdapter salonPersistenceAdapter;

    @Autowired
    private SalonJpaRepository repository;

    @Autowired
    private SalonPersistenceMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private SalonSchedulingConfig salonSchedulingConfig;

    @Test
    void savingAStaleFullAggregateDoesNotOverwriteAnAlreadyCompletedTimestampWithNull() {
        String tenantId = newTenantId();
        saveAndDetach(pendingSalon(tenantId));

        int firstCompletion = repository.markOnboardingCompletedIfPending(tenantId, Instant.now());
        assertThat(firstCompletion)
                .as("the seeded row must genuinely start pending, so the timestamp below is real")
                .isEqualTo(1);
        Instant completedAt = repository.findByTenantId(tenantId).orElseThrow().getOnboardingCompletedAt();
        assertThat(completedAt).isNotNull();
        entityManager.clear();

        // Reconstructs exactly what SalonService#updateStatus builds: the whole aggregate, read
        // fresh, with only one field about to change. onboardingCompletedAt is forced back to null
        // to stand in for a caller that read this row a moment earlier, before the
        // markOnboardingCompletedIfPending call above landed - the race updatable = false exists to
        // close, reproduced here as a direct state manipulation instead of real thread timing.
        Salon staleAggregate = mapper.toDomain(repository.findByTenantId(tenantId).orElseThrow());
        staleAggregate.setOnboardingCompletedAt(null);
        staleAggregate.setStatus(SalonStatus.INACTIVE);

        salonPersistenceAdapter.save(staleAggregate);
        entityManager.flush();
        entityManager.clear();

        SalonJpaEntity reloaded = repository.findByTenantId(tenantId).orElseThrow();
        assertThat(reloaded.getOnboardingCompletedAt())
                .as("a full-aggregate save of a stale read must not erase the timestamp a concurrent "
                        + "compare-and-set already committed")
                .isEqualTo(completedAt);
        assertThat(reloaded.getStatus())
                .as("the field this save legitimately intended to change must still go through - "
                        + "this is not a save that silently does nothing")
                .isEqualTo(SalonStatus.INACTIVE);
    }

    private SalonJpaEntity saveAndDetach(SalonJpaEntity entity) {
        SalonJpaEntity saved = repository.saveAndFlush(entity);
        entityManager.clear();
        return saved;
    }

    private static SalonJpaEntity pendingSalon(String tenantId) {
        SalonJpaEntity entity = new SalonJpaEntity();
        entity.setTenantId(tenantId);
        entity.setExternalId(tenantId);
        entity.setName("Integration Test Salon");
        entity.setEmail(tenantId + "@example.com");
        entity.setPhone("+34600000000");
        entity.setAddressStreet("Carrer Test 1");
        entity.setAddressPostalCode("08001");
        entity.setAddressCity("Barcelona");
        entity.setTimezone("Europe/Madrid");
        entity.setCurrency("EUR");
        entity.setSubscriptionPlan(SubscriptionPlan.FREE_TRIAL);
        entity.setStatus(SalonStatus.ACTIVE);
        // onboardingCompletedAt left null: this is the "pending" state under test.
        return entity;
    }

    private static String newTenantId() {
        // Same shape as production tenant ids (sal_ + UUID, see module CLAUDE.md), kept under the
        // 44-char column limit (4 + 36 = 40).
        return "sal_" + UUID.randomUUID();
    }
}
