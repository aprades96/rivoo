package com.rivoo.salon.infrastructure.adapter.out.persistence.repository;

import com.rivoo.salon.domain.model.SalonStatus;
import com.rivoo.salon.domain.model.SubscriptionPlan;
import com.rivoo.salon.infrastructure.adapter.out.persistence.entity.SalonJpaEntity;
import com.rivoo.salon.infrastructure.config.SalonSchedulingConfig;
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
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Exercises the real JPQL behind {@link SalonJpaRepository#markOnboardingCompletedIfPending(String,
 * Instant)} against a genuine MySQL connection - the piece of coverage
 * {@code SalonOnboardingCompletionTest} (package {@code com.rivoo.salon.application}) explicitly
 * does NOT provide, since that one drives a hand-written in-memory fake, never this query. Three
 * mutations of that query - dropping the {@code onboardingCompletedAt IS NULL} predicate, writing
 * only {@code updatedAt} while leaving {@code onboardingCompletedAt} untouched, or having the
 * adapter never call the repository at all - used to leave the whole 88-test suite green; this
 * class (together with {@code SalonPersistenceAdapterOnboardingCompletionTest} for the third one,
 * which lives one layer up and is unreachable from here - see below) closes that gap.
 * <p>
 * <b>How to run it.</b> It is tagged {@code @Tag("integration")}, which the root {@code pom.xml}
 * excludes from the default {@code surefire.excludedGroups} (see root {@code pom.xml:42-43,159-164}).
 * Run it explicitly with:
 * <pre>{@code
 * mvn -o -pl salon-service -am test -Dtest=SalonJpaRepositoryOnboardingCompletionIntegrationTest -Dsurefire.excluded.groups=
 * }</pre>
 * against a running local MySQL (matches the connection {@code application-local.yml} already
 * declares: {@code 127.0.0.1:3306}, schema {@code salon_db}, user {@code rivoo}).
 * <p>
 * <b>Why not Testcontainers, despite that being this repository's documented convention for
 * integration tests</b> (see {@code AppointmentRepositoryIntegrationTest},
 * {@code BillingRepositoryIntegrationTest})</b>: Docker is not available in this environment. This
 * test instead activates the {@code local} Spring profile, whose {@code application-local.yml}
 * (in {@code src/main/resources}, therefore already on the test classpath) points at the MySQL
 * instance that IS available, already Flyway-migrated to the current schema. This is a deliberate,
 * environment-forced deviation from the module's stated convention, called out here rather than
 * left for someone to trip over later.
 * <p>
 * <b>Why {@code @SpringBootTest} and not {@code @DataJpaTest}.</b> Spring Boot 4.0.3, as actually
 * resolved on this machine's local Maven repository, no longer ships a {@code @DataJpaTest} or
 * {@code @AutoConfigureTestDatabase} annotation at all (verified directly: no class by either name
 * exists in any {@code spring-boot-test-autoconfigure} jar under {@code ~/.m2}, of any version
 * present, that this module actually resolves). A full {@code @SpringBootTest} is therefore the
 * only available way in this module to obtain a real, Spring-wired {@link SalonJpaRepository} bean -
 * which is also what both existing Testcontainers-based integration tests in this repository already
 * do, so this follows that same shape modulo the datasource.
 * <p>
 * <b>Isolation - the database is left exactly as found.</b> The test class is
 * {@code @Transactional}; every test method therefore runs inside one Spring-managed transaction
 * bound to the real connection, which the {@code TransactionalTestExecutionListener} wired in by
 * default for {@code @SpringBootTest} rolls back once the method returns. The two rows a test
 * creates never survive past that method's end, and the pre-existing salons already in
 * {@code salon_db} are never read, let alone written. The bulk JPQL under test still genuinely runs
 * and its effect is still visible to the reads later in the same method:
 * {@code @Modifying(flushAutomatically = true)} flushes it to the real connection: it is simply
 * never committed.
 * <p>
 * <b>{@link SalonSchedulingConfig} is replaced with a Mockito mock</b> purely as a safety net, not
 * because it was observed to interfere: its {@code cleanupStaleOnboardings} job runs on a fixed
 * 5-minute rate starting at context boot (first run effectively immediate) against
 * {@code SalonStatus.ONBOARDING} rows older than one hour, and every row this test seeds is
 * {@code ACTIVE}, so it has nothing to match today. Mocking the bean removes the dependency on that
 * staying true, and on the schema's 15 pre-existing salons (verified empty of {@code ONBOARDING}
 * rows at the time of writing) never containing one that job could legitimately touch.
 * <p>
 * <b>No {@code TenantContext} is set</b>, unlike {@code AppointmentRepositoryIntegrationTest}: every
 * query this test relies on ({@code markOnboardingCompletedIfPending}, {@code findByTenantId})
 * already carries its own explicit {@code tenant_id} predicate (see both queries' javadoc on
 * {@link SalonJpaRepository}), so the Hibernate {@code @Filter} plays no part here - and activating
 * it for one tenant would actively get in the way, since {@link
 * #completingOneTenantsOnboardingDoesNotTouchAnotherPendingTenantsRow()} deliberately reads and
 * writes rows for two different tenants in the same method.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
@Tag("integration")
class SalonJpaRepositoryOnboardingCompletionIntegrationTest {

    @Autowired
    private SalonJpaRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private SalonSchedulingConfig salonSchedulingConfig;

    /**
     * {@code saveAndFlush} leaves the saved entity managed: a later {@code findByTenantId} for the
     * same row would return that SAME Java instance from Hibernate's first-level cache, still
     * holding the sub-second {@code Instant} {@code @PrePersist} set in memory, not what MySQL
     * actually stored (its {@code TIMESTAMP} columns have second precision). Clearing the
     * persistence context after seeding forces every subsequent read in these tests - "before" and
     * "after" alike - through a genuine {@code SELECT}, so they are comparable on equal terms.
     */
    private SalonJpaEntity saveAndDetach(SalonJpaEntity entity) {
        SalonJpaEntity saved = repository.saveAndFlush(entity);
        entityManager.clear();
        return saved;
    }

    @Test
    void firstCallOnAPendingSalonWritesTheTimestampAndReturnsOne() {
        SalonJpaEntity salon = saveAndDetach(pendingSalon(newTenantId()));
        Instant beforeCall = Instant.now();

        int updated = repository.markOnboardingCompletedIfPending(salon.getTenantId(), beforeCall);

        assertThat(updated)
                .as("the first call against a pending row must change exactly one row")
                .isEqualTo(1);
        SalonJpaEntity reloaded = repository.findByTenantId(salon.getTenantId()).orElseThrow();
        assertThat(reloaded.getOnboardingCompletedAt())
                .as("onboardingCompletedAt must actually be written, not left null")
                .isNotNull()
                .isCloseTo(beforeCall, within(2, ChronoUnit.SECONDS));
    }

    @Test
    void secondCallReturnsZeroAndDoesNotMoveTheTimestampTheFirstCallWrote() {
        SalonJpaEntity salon = saveAndDetach(pendingSalon(newTenantId()));

        int firstResult = repository.markOnboardingCompletedIfPending(salon.getTenantId(), Instant.now());
        assertThat(firstResult).isEqualTo(1);
        Instant firstTimestamp = repository.findByTenantId(salon.getTenantId())
                .orElseThrow()
                .getOnboardingCompletedAt();
        assertThat(firstTimestamp).isNotNull();

        int secondResult = repository.markOnboardingCompletedIfPending(salon.getTenantId(), Instant.now());

        assertThat(secondResult)
                .as("a double click, two tabs or a retry must change zero rows the second time")
                .isEqualTo(0);
        SalonJpaEntity reloaded = repository.findByTenantId(salon.getTenantId()).orElseThrow();
        assertThat(reloaded.getOnboardingCompletedAt())
                .as("the timestamp the first call wrote must not move")
                .isEqualTo(firstTimestamp);
    }

    @Test
    void completingOneTenantsOnboardingDoesNotTouchAnotherPendingTenantsRow() {
        SalonJpaEntity target = saveAndDetach(pendingSalon(newTenantId()));
        SalonJpaEntity otherTenant = saveAndDetach(pendingSalon(newTenantId()));
        // Read back through the repository, not the just-saved in-memory instance: updated_at is a
        // MySQL TIMESTAMP with second precision, so the DB-truncated value can legitimately differ
        // from the sub-second Instant @PrePersist set in memory. Comparing against that in-memory
        // value would be a false mismatch, not evidence of the row having been touched.
        Instant otherTenantUpdatedAtBefore = repository.findByTenantId(otherTenant.getTenantId())
                .orElseThrow()
                .getUpdatedAt();

        int updated = repository.markOnboardingCompletedIfPending(target.getTenantId(), Instant.now());

        assertThat(updated).isEqualTo(1);
        SalonJpaEntity reloadedOther = repository.findByTenantId(otherTenant.getTenantId()).orElseThrow();
        assertThat(reloadedOther.getOnboardingCompletedAt())
                .as("tenant_id is part of the predicate - a different tenant's row must stay untouched")
                .isNull();
        assertThat(reloadedOther.getUpdatedAt())
                .as("an untouched row's updatedAt must not move either")
                .isEqualTo(otherTenantUpdatedAtBefore);
    }

    @Test
    void callingForATenantIdThatMatchesNoRowReturnsZero() {
        SalonJpaEntity unrelated = saveAndDetach(pendingSalon(newTenantId()));

        int updated = repository.markOnboardingCompletedIfPending(newTenantId(), Instant.now());

        assertThat(updated).isZero();
        assertThat(repository.findByTenantId(unrelated.getTenantId()).orElseThrow().getOnboardingCompletedAt())
                .as("a call for an unrelated tenant id must not touch any other pending row either")
                .isNull();
    }

    @Test
    void theBulkUpdateAdvancesUpdatedAtEvenThoughItBypassesThePreUpdateCallback() throws InterruptedException {
        SalonJpaEntity salon = saveAndDetach(pendingSalon(newTenantId()));
        Instant createdUpdatedAt = repository.findByTenantId(salon.getTenantId()).orElseThrow().getUpdatedAt();

        // The updated_at column is a MySQL TIMESTAMP with second precision (no fractional-seconds
        // part), so a difference smaller than a full second could round away in either direction.
        // Sleeping comfortably past one second makes the assertion below deterministic instead of
        // occasionally flaky.
        Thread.sleep(1500);

        int updated = repository.markOnboardingCompletedIfPending(salon.getTenantId(), Instant.now());

        assertThat(updated).isEqualTo(1);
        Instant updatedAtAfterCompletion = repository.findByTenantId(salon.getTenantId())
                .orElseThrow()
                .getUpdatedAt();
        assertThat(updatedAtAfterCompletion)
                .as("the bulk JPQL sets updatedAt explicitly because @PreUpdate never fires for it")
                .isAfter(createdUpdatedAt);
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
