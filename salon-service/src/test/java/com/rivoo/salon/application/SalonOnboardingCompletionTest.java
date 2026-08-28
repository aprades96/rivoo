package com.rivoo.salon.application;

import com.rivoo.salon.application.dto.SalonResponse;
import com.rivoo.salon.domain.exception.SalonNotFoundException;
import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonBusinessHours;
import com.rivoo.salon.domain.model.SalonStatus;
import com.rivoo.salon.domain.model.SubscriptionPlan;
import com.rivoo.salon.domain.port.out.BusinessHoursPersistencePort;
import com.rivoo.salon.domain.port.out.NotificationServicePort;
import com.rivoo.salon.domain.port.out.SalonPersistencePort;
import com.rivoo.salon.domain.port.out.StaffServicePort;
import com.rivoo.salon.infrastructure.mapper.SalonDtoMapperImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code SalonService#completeOnboarding} is the write side of the onboarding-completion timestamp:
 * the endpoint the setup assistant calls once it is done. It must be idempotent, because a double
 * click, two open tabs or a retried request must not move a timestamp that a previous call already
 * wrote - and it must not let one tenant touch another's row.
 * <p>
 * Follows the same shape as {@link SalonRegistrationPublicVisibilityTest}: an in-memory
 * {@link SalonPersistencePort} written by hand (not a mock), {@link SalonDtoMapperImpl}
 * instantiated directly, and a {@link CyclicBarrier} to force two threads to start together rather
 * than happen to run one after the other.
 * <p>
 * <b>What this class does and does not prove.</b> Every test here, including
 * {@link #twoConcurrentCallsProduceExactlyOneWrite()}, runs against {@code FakeSalonStore}, whose
 * {@code markOnboardingCompleted} is itself {@code synchronized}: the mutual exclusion between the
 * two threads in that test is provided by the test double, not observed as an emergent property of
 * {@link SalonService}. What this class DOES prove - and what is actually load-bearing - is that
 * {@code SalonService#completeOnboarding} performs no read-decide-write of its own: it delegates the
 * check and the write to a single call on {@link SalonPersistencePort#markOnboardingCompleted}, so
 * whatever atomicity the port implementation provides is not undermined by the service racing ahead
 * of it.
 * <p>
 * <b>What this class does NOT prove.</b> The real atomicity guarantee - the
 * {@code WHERE ... onboarding_completed_at IS NULL} predicate in the JPQL update
 * ({@code SalonJpaRepository#markOnboardingCompletedIfPending}) actually being enforced by MySQL -
 * has no coverage here, by construction: {@code FakeSalonStore} is a hand-written in-memory double,
 * not the real query. That guarantee is exercised separately, against a real MySQL instance, by
 * {@code SalonJpaRepositoryOnboardingCompletionIntegrationTest} (package
 * {@code infrastructure.adapter.out.persistence.repository}, tagged {@code @Tag("integration")},
 * excluded from the default build - see that class's javadoc for the exact command to run it).
 */
class SalonOnboardingCompletionTest {

    private static final String TENANT_ID = "sal_onboarding_done";
    private static final String OTHER_TENANT_ID = "sal_other_tenant";

    @Test
    void salonWithNoCompletionDateGetsATimestampWhenOnboardingCompletes() {
        FakeSalonStore salons = new FakeSalonStore();
        salons.seed(pendingSalon(TENANT_ID));
        SalonService salonService = newSalonService(salons);

        SalonResponse response = salonService.completeOnboarding(TENANT_ID);

        assertThat(response.onboardingCompletedAt()).isNotNull();
        assertThat(salons.findByTenantId(TENANT_ID).orElseThrow().getOnboardingCompletedAt())
                .isNotNull();
    }

    @Test
    void secondCallDoesNotChangeTheTimestampTheFirstCallWrote() {
        FakeSalonStore salons = new FakeSalonStore();
        salons.seed(pendingSalon(TENANT_ID));
        SalonService salonService = newSalonService(salons);

        int writesAfterSeeding = salons.writes.get();

        SalonResponse first = salonService.completeOnboarding(TENANT_ID);
        Instant firstTimestamp = first.onboardingCompletedAt();
        assertThat(firstTimestamp).isNotNull();

        SalonResponse second = salonService.completeOnboarding(TENANT_ID);

        assertThat(second.onboardingCompletedAt())
                .as("a double click, two tabs or a retry must keep the first call's timestamp")
                .isEqualTo(firstTimestamp);
        assertThat(salons.markOnboardingAttempts.get())
                .as("both calls must genuinely have tried the conditional write")
                .isEqualTo(2);
        assertThat(salons.writes.get() - writesAfterSeeding)
                .as("only the first call may have actually changed a row")
                .isEqualTo(1);
    }

    @Test
    void twoConcurrentCallsProduceExactlyOneWrite() throws Exception {
        FakeSalonStore salons = new FakeSalonStore();
        salons.seed(pendingSalon(TENANT_ID));
        int writesAfterSeeding = salons.writes.get();
        SalonService salonService = newSalonService(salons);

        CyclicBarrier startTogether = new CyclicBarrier(2);
        CountDownLatch done = new CountDownLatch(2);
        List<Throwable> failures = new ArrayList<>();
        Runnable completeOnboarding = () -> {
            try {
                startTogether.await(10, TimeUnit.SECONDS);
                salonService.completeOnboarding(TENANT_ID);
            } catch (Throwable t) {
                synchronized (failures) {
                    failures.add(t);
                }
            } finally {
                done.countDown();
            }
        };
        Thread first = new Thread(completeOnboarding, "complete-onboarding-1");
        Thread second = new Thread(completeOnboarding, "complete-onboarding-2");
        first.start();
        second.start();

        assertThat(done.await(10, TimeUnit.SECONDS))
                .as("both calls must finish; a deadlock here is a failure, not a pass")
                .isTrue();
        assertThat(failures).isEmpty();
        assertThat(salons.markOnboardingAttempts.get())
                .as("both callers must genuinely have tried; one winning by not trying proves nothing")
                .isEqualTo(2);
        assertThat(salons.writes.get() - writesAfterSeeding)
                .as("exactly one of the two concurrent callers may have written the row")
                .isEqualTo(1);
        assertThat(salons.findByTenantId(TENANT_ID).orElseThrow().getOnboardingCompletedAt())
                .isNotNull();
    }

    @Test
    void tenantCannotCompleteAnotherTenantsOnboarding() {
        FakeSalonStore salons = new FakeSalonStore();
        salons.seed(pendingSalon(TENANT_ID));
        salons.seed(pendingSalon(OTHER_TENANT_ID));
        SalonService salonService = newSalonService(salons);

        salonService.completeOnboarding(TENANT_ID);

        assertThat(salons.findByTenantId(TENANT_ID).orElseThrow().getOnboardingCompletedAt())
                .as("the caller's own tenant must be completed")
                .isNotNull();
        assertThat(salons.findByTenantId(OTHER_TENANT_ID).orElseThrow().getOnboardingCompletedAt())
                .as("a different tenant's row must be untouched - tenantId is part of the predicate")
                .isNull();
    }

    @Test
    void tenantWithNoSalonAtAllGetsSalonNotFoundInsteadOfBeingTreatedAsAlreadyCompleted() {
        FakeSalonStore salons = new FakeSalonStore();
        SalonService salonService = newSalonService(salons);

        // markOnboardingCompleted returns 0 both when the row exists but is already completed and
        // when the tenant has no row at all - the two situations SalonService.completeOnboarding
        // tells apart by re-reading. This is the second one: no salon was ever seeded for this
        // tenant.
        assertThatThrownBy(() -> salonService.completeOnboarding("sal_does_not_exist"))
                .isInstanceOf(SalonNotFoundException.class);
    }

    // -- helpers --------------------------------------------------------------

    private static SalonService newSalonService(FakeSalonStore salons) {
        return new SalonService(
                salons,
                new FakeBusinessHoursStore(),
                new EmptyCatalogueStaffService(),
                new SalonDtoMapperImpl(),
                new SalonPublicSnapshotLoader(salons, new FakeBusinessHoursStore()),
                new NoOpNotificationService());
    }

    private static Salon pendingSalon(String tenantId) {
        return Salon.builder()
                .externalId(tenantId)
                .tenantId(tenantId)
                .name("Onboarding Salon")
                .slug(tenantId + "-slug")
                .ownerUserId("9f1c2d3e-0000-4444-8888-aaaabbbbcccc")
                .email(tenantId + "@example.com")
                .phone("+34600111222")
                .addressStreet("Carrer Demo 1")
                .addressCity("Barcelona")
                .addressPostalCode("08001")
                .timezone("Europe/Madrid")
                .currency("EUR")
                .subscriptionPlan(SubscriptionPlan.FREE_TRIAL)
                .status(SalonStatus.ACTIVE)
                .onboardingCompletedAt(null)
                .build();
    }

    /**
     * Same in-memory shape as {@code SalonRegistrationPublicVisibilityTest.SalonStore} (that one is
     * {@code private} to its own top-level class, so it cannot be reused here): a genuine store, not
     * a stub, so the compare-and-set on {@code onboardingCompletedAt} is really exercised, and
     * {@code synchronized} so two racing callers cannot both observe the pending state and both
     * decide they were the one who wrote it.
     */
    private static final class FakeSalonStore implements SalonPersistencePort {

        private final Map<Long, Salon> rows = new LinkedHashMap<>();
        private long sequence = 0L;

        /** How many times the conditional onboarding-completion update was issued at all. */
        final AtomicInteger markOnboardingAttempts = new AtomicInteger();
        /** Every row-touching operation: {@code save} plus each successful completion. */
        final AtomicInteger writes = new AtomicInteger();

        void seed(Salon salon) {
            save(salon);
        }

        @Override
        public synchronized Salon save(Salon salon) {
            writes.incrementAndGet();
            if (salon.getId() == null) {
                salon.setId(++sequence);
                salon.setCreatedAt(Instant.now());
            }
            salon.setUpdatedAt(Instant.now());
            rows.put(salon.getId(), copyOf(salon));
            return copyOf(salon);
        }

        @Override
        public synchronized Optional<Salon> findByTenantId(String tenantId) {
            return rows.values().stream()
                    .filter(s -> tenantId.equals(s.getTenantId()))
                    .findFirst()
                    .map(FakeSalonStore::copyOf);
        }

        @Override
        public synchronized Optional<Salon> findBySlug(String slug) {
            return rows.values().stream()
                    .filter(s -> slug.equals(s.getSlug()))
                    .findFirst()
                    .map(FakeSalonStore::copyOf);
        }

        @Override
        public synchronized boolean existsBySlug(String slug) {
            return rows.values().stream().anyMatch(s -> slug.equals(s.getSlug()));
        }

        @Override
        public synchronized boolean existsByEmail(String email) {
            return rows.values().stream().anyMatch(s -> email.equals(s.getEmail()));
        }

        @Override
        public synchronized void deleteById(Long id) {
            rows.remove(id);
        }

        @Override
        public Page<Salon> findAll(Pageable pageable) {
            return Page.empty(pageable);
        }

        @Override
        public synchronized List<Salon> findByStatusAndCreatedAtBefore(SalonStatus status, Instant before) {
            return rows.values().stream()
                    .filter(s -> s.getStatus() == status
                            && s.getCreatedAt() != null && s.getCreatedAt().isBefore(before))
                    .map(FakeSalonStore::copyOf)
                    .toList();
        }

        @Override
        public synchronized int activateIfOnboarding(String tenantId) {
            throw new UnsupportedOperationException("not exercised by this test");
        }

        /**
         * Compare-and-set, calcado de {@code activateIfOnboarding} in
         * {@code SalonRegistrationPublicVisibilityTest.SalonStore}: the check
         * ({@code onboardingCompletedAt == null}) and the write happen inside the same
         * {@code synchronized} block, so two racing callers cannot both observe {@code null} and
         * both believe they were the one who completed onboarding.
         */
        @Override
        public synchronized int markOnboardingCompleted(String tenantId) {
            markOnboardingAttempts.incrementAndGet();
            Optional<Salon> match = rows.values().stream()
                    .filter(s -> tenantId.equals(s.getTenantId()) && s.getOnboardingCompletedAt() == null)
                    .findFirst();
            if (match.isEmpty()) {
                return 0;
            }
            match.get().setOnboardingCompletedAt(Instant.now());
            match.get().setUpdatedAt(Instant.now());
            writes.incrementAndGet();
            return 1;
        }

        private static Salon copyOf(Salon s) {
            return Salon.builder()
                    .id(s.getId()).externalId(s.getExternalId()).tenantId(s.getTenantId())
                    .name(s.getName()).slug(s.getSlug()).ownerUserId(s.getOwnerUserId())
                    .email(s.getEmail()).phone(s.getPhone()).description(s.getDescription())
                    .logoUrl(s.getLogoUrl()).primaryColor(s.getPrimaryColor())
                    .addressStreet(s.getAddressStreet()).addressCity(s.getAddressCity())
                    .addressPostalCode(s.getAddressPostalCode()).timezone(s.getTimezone())
                    .currency(s.getCurrency()).subscriptionPlan(s.getSubscriptionPlan())
                    .status(s.getStatus()).onboardingCompletedAt(s.getOnboardingCompletedAt())
                    .createdAt(s.getCreatedAt()).updatedAt(s.getUpdatedAt())
                    .build();
        }
    }

    private static final class FakeBusinessHoursStore implements BusinessHoursPersistencePort {

        private final List<SalonBusinessHours> rows = new ArrayList<>();

        @Override
        public synchronized List<SalonBusinessHours> findBySalonId(Long salonId) {
            return rows.stream().filter(h -> salonId.equals(h.getSalonId())).toList();
        }

        @Override
        public synchronized List<SalonBusinessHours> saveAll(List<SalonBusinessHours> hours) {
            rows.addAll(hours);
            return List.copyOf(hours);
        }

        @Override
        public synchronized void deleteBySalonId(Long salonId) {
            rows.removeIf(h -> salonId.equals(h.getSalonId()));
        }
    }

    private static final class EmptyCatalogueStaffService implements StaffServicePort {

        @Override
        public Optional<List<EmployeePublicInfo>> getPublicEmployees(String tenantId) {
            return Optional.of(List.of());
        }

        @Override
        public Optional<List<ServicePublicInfo>> getPublicServices(String tenantId) {
            return Optional.of(List.of());
        }
    }

    private static final class NoOpNotificationService implements NotificationServicePort {

        @Override
        public void sendWelcomeEmail(String tenantId, String recipientEmail, String salonName) {
            // completeOnboarding never sends mail; a call here would be a bug, but nothing in
            // this test triggers one.
        }

        @Override
        public void sendExistingAccountRegistrationAttempt(String recipientEmail) {
            throw new AssertionError("completeOnboarding must never trigger a registration-attempt mail");
        }
    }
}
