package com.rivoo.billing.infrastructure.adapter.out.persistence;

import com.rivoo.billing.domain.model.PlanName;
import com.rivoo.billing.domain.model.SubscriptionStatus;
import com.rivoo.billing.infrastructure.adapter.out.persistence.entity.PlanLimitJpaEntity;
import com.rivoo.billing.infrastructure.adapter.out.persistence.entity.SubscriptionJpaEntity;
import com.rivoo.billing.infrastructure.adapter.out.persistence.entity.SubscriptionPlanJpaEntity;
import com.rivoo.billing.infrastructure.adapter.out.persistence.repository.PlanLimitJpaRepository;
import com.rivoo.billing.infrastructure.adapter.out.persistence.repository.SubscriptionJpaRepository;
import com.rivoo.billing.infrastructure.adapter.out.persistence.repository.SubscriptionPlanJpaRepository;
import com.rivoo.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests that verify Flyway migrations, seed data, and the persistence layer
 * for billing-service using a temporary MySQL container per test run.
 *
 * <p>TenantContext usage:
 * - SubscriptionJpaEntity extends TenantAwareEntity — tenant_id is mandatory.
 *   TenantEntityListener reads TenantContext on @PrePersist.
 * - TenantFilterAspect activates the Hibernate tenantFilter only when
 *   TenantContext is non-null. Tests that need to bypass the filter (e.g. findAll
 *   across multiple subscriptions) must keep TenantContext cleared.
 * - Tests that persist a subscription set TenantContext before save and clear it
 *   after the assertion to avoid filter side-effects on subsequent queries.
 *
 * <p>SubscriptionPlanJpaEntity and PlanLimitJpaEntity are NOT tenant-aware, so
 * they do not require TenantContext to be set.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
class BillingRepositoryIntegrationTest {

    // A single container is shared across all tests in this class (static field).
    // Testcontainers starts it once and @ServiceConnection wires the datasource automatically.
    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("billing_db");

    @Autowired
    private SubscriptionPlanJpaRepository planRepository;

    @Autowired
    private PlanLimitJpaRepository limitRepository;

    @Autowired
    private SubscriptionJpaRepository subscriptionRepository;

    // Always clear TenantContext after each test to avoid cross-test contamination.
    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ── Flyway seed: plan count ───────────────────────────────────────────

    @Test
    void seedData_fourPlansExist() {
        List<SubscriptionPlanJpaEntity> plans = planRepository.findAll();

        assertThat(plans).hasSize(4);
        assertThat(plans.stream().map(p -> p.getName().name()).toList())
                .containsExactlyInAnyOrder("FREE_TRIAL", "BASIC", "PREMIUM", "ENTERPRISE");
    }

    @Test
    void seedData_onlyActivePlansReturnedByFindByActiveTrue() {
        List<SubscriptionPlanJpaEntity> active = planRepository.findByActiveTrue();

        // V3 seeds all 4 plans as active = TRUE
        assertThat(active).hasSize(4);
        assertThat(active).allMatch(SubscriptionPlanJpaEntity::isActive);
    }

    // ── Flyway seed: plan details ─────────────────────────────────────────

    @Test
    void seedData_basicPlan_costs29EurosAndHasCorrectDisplayName() {
        SubscriptionPlanJpaEntity basic = planRepository.findByName(PlanName.BASIC).orElseThrow();

        assertThat(basic.getMonthlyPrice()).isEqualByComparingTo(new BigDecimal("29.00"));
        assertThat(basic.getDisplayName()).isEqualTo("Rivoo - Basic");
        assertThat(basic.getTrialDays()).isZero();
    }

    @Test
    void seedData_premiumPlan_costs59Euros() {
        SubscriptionPlanJpaEntity premium = planRepository.findByName(PlanName.PREMIUM).orElseThrow();

        assertThat(premium.getMonthlyPrice()).isEqualByComparingTo(new BigDecimal("59.00"));
        assertThat(premium.getDisplayName()).isEqualTo("Rivoo - Premium");
    }

    @Test
    void seedData_enterprisePlan_costs99Euros() {
        SubscriptionPlanJpaEntity enterprise = planRepository.findByName(PlanName.ENTERPRISE).orElseThrow();

        assertThat(enterprise.getMonthlyPrice()).isEqualByComparingTo(new BigDecimal("99.00"));
        assertThat(enterprise.getDisplayName()).isEqualTo("Rivoo - Enterprise");
    }

    @Test
    void seedData_freeTrial_hasZeroPriceAnd14TrialDays() {
        SubscriptionPlanJpaEntity freeTrial = planRepository.findByName(PlanName.FREE_TRIAL).orElseThrow();

        assertThat(freeTrial.getMonthlyPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(freeTrial.getTrialDays()).isEqualTo(14);
        assertThat(freeTrial.getDisplayName()).isEqualTo("Free Trial");
    }

    @Test
    void seedData_allPlans_haveUniqueExternalIds() {
        List<SubscriptionPlanJpaEntity> plans = planRepository.findAll();
        long distinctIds = plans.stream().map(SubscriptionPlanJpaEntity::getExternalId).distinct().count();

        assertThat(distinctIds).isEqualTo(plans.size());
        // external_ids use the pln_ prefix per project conventions
        assertThat(plans).allMatch(p -> p.getExternalId().startsWith("pln_"));
    }

    // ── Flyway seed: plan limits count ────────────────────────────────────

    @Test
    void seedData_sixteenLimitsExistInTotal() {
        // 4 plans × 4 limit keys each = 16 rows in plan_limits
        List<PlanLimitJpaEntity> all = limitRepository.findAll();

        assertThat(all).hasSize(16);
    }

    // ── Flyway seed: FREE_TRIAL limits ────────────────────────────────────

    @Test
    void seedData_freeTrial_hasCorrectLimits() {
        SubscriptionPlanJpaEntity freeTrial = planRepository.findByName(PlanName.FREE_TRIAL).orElseThrow();
        List<PlanLimitJpaEntity> limits = limitRepository.findByPlanId(freeTrial.getId());

        assertThat(limits).hasSize(4);
        assertThat(findLimitValue(limits, "max_employees")).isEqualTo(1);
        assertThat(findLimitValue(limits, "max_appointments_per_month")).isEqualTo(50);
        assertThat(findLimitValue(limits, "email_reminders_enabled")).isZero();
        assertThat(findLimitValue(limits, "sms_reminders_enabled")).isZero();
    }

    // ── Flyway seed: BASIC limits ─────────────────────────────────────────

    @Test
    void seedData_basic_hasCorrectLimits() {
        SubscriptionPlanJpaEntity basic = planRepository.findByName(PlanName.BASIC).orElseThrow();
        List<PlanLimitJpaEntity> limits = limitRepository.findByPlanId(basic.getId());

        assertThat(findLimitValue(limits, "max_employees")).isEqualTo(3);
        assertThat(findLimitValue(limits, "max_appointments_per_month")).isEqualTo(200);
        assertThat(findLimitValue(limits, "email_reminders_enabled")).isEqualTo(1);
        assertThat(findLimitValue(limits, "sms_reminders_enabled")).isZero();
    }

    // ── Flyway seed: PREMIUM limits ───────────────────────────────────────

    @Test
    void seedData_premium_hasCorrectLimits() {
        SubscriptionPlanJpaEntity premium = planRepository.findByName(PlanName.PREMIUM).orElseThrow();
        List<PlanLimitJpaEntity> limits = limitRepository.findByPlanId(premium.getId());

        assertThat(findLimitValue(limits, "max_employees")).isEqualTo(10);
        // -1 means unlimited
        assertThat(findLimitValue(limits, "max_appointments_per_month")).isEqualTo(-1);
        assertThat(findLimitValue(limits, "email_reminders_enabled")).isEqualTo(1);
        assertThat(findLimitValue(limits, "sms_reminders_enabled")).isEqualTo(1);
    }

    // ── Flyway seed: ENTERPRISE limits ────────────────────────────────────

    @Test
    void seedData_enterprise_hasUnlimitedEmployeesAndAppointments() {
        SubscriptionPlanJpaEntity enterprise = planRepository.findByName(PlanName.ENTERPRISE).orElseThrow();
        List<PlanLimitJpaEntity> limits = limitRepository.findByPlanId(enterprise.getId());

        assertThat(findLimitValue(limits, "max_employees")).isEqualTo(-1);
        assertThat(findLimitValue(limits, "max_appointments_per_month")).isEqualTo(-1);
        assertThat(findLimitValue(limits, "email_reminders_enabled")).isEqualTo(1);
        assertThat(findLimitValue(limits, "sms_reminders_enabled")).isEqualTo(1);
    }

    // ── PlanLimitJpaRepository: findByPlanIdAndLimitKey ───────────────────

    @Test
    void findByPlanIdAndLimitKey_returnsCorrectRow() {
        SubscriptionPlanJpaEntity freeTrial = planRepository.findByName(PlanName.FREE_TRIAL).orElseThrow();

        Optional<PlanLimitJpaEntity> result = limitRepository.findByPlanIdAndLimitKey(
                freeTrial.getId(), "max_employees");

        assertThat(result).isPresent();
        assertThat(result.get().getLimitValue()).isEqualTo(1);
        assertThat(result.get().getLimitKey()).isEqualTo("max_employees");
    }

    @Test
    void findByPlanIdAndLimitKey_unknownKey_returnsEmpty() {
        SubscriptionPlanJpaEntity basic = planRepository.findByName(PlanName.BASIC).orElseThrow();

        Optional<PlanLimitJpaEntity> result = limitRepository.findByPlanIdAndLimitKey(
                basic.getId(), "non_existent_key");

        assertThat(result).isEmpty();
    }

    // ── SubscriptionJpaRepository: save and find ─────────────────────────

    @Test
    void subscription_saveAndFindByTenantId() {
        String tenantId = "sal_tenant_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Long planId = planRepository.findByName(PlanName.FREE_TRIAL).orElseThrow().getId();

        // TenantContext must be set so TenantEntityListener can populate tenant_id on @PrePersist
        TenantContext.setCurrentTenantId(tenantId);
        SubscriptionJpaEntity saved = subscriptionRepository.save(buildSubscription(tenantId, planId));
        // Clear immediately after save — TenantFilterAspect must not filter the upcoming findByTenantId
        TenantContext.clear();

        Optional<SubscriptionJpaEntity> found = subscriptionRepository.findByTenantId(tenantId);

        assertThat(found).isPresent();
        assertThat(found.get().getTenantId()).isEqualTo(tenantId);
        assertThat(found.get().getStatus()).isEqualTo(SubscriptionStatus.TRIALING);
        assertThat(found.get().getPlanId()).isEqualTo(planId);
        assertThat(found.get().getExternalId()).startsWith("sub_");
    }

    @Test
    void subscription_savePopulatesCreatedAtAndUpdatedAt() {
        String tenantId = "sal_ts_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Long planId = planRepository.findByName(PlanName.FREE_TRIAL).orElseThrow().getId();

        TenantContext.setCurrentTenantId(tenantId);
        SubscriptionJpaEntity saved = subscriptionRepository.save(buildSubscription(tenantId, planId));
        TenantContext.clear();

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isEqualTo(saved.getUpdatedAt());
    }

    @Test
    void subscription_existsByTenantId_returnsTrueWhenExists() {
        String tenantId = "sal_ex_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Long planId = planRepository.findByName(PlanName.FREE_TRIAL).orElseThrow().getId();

        TenantContext.setCurrentTenantId(tenantId);
        subscriptionRepository.save(buildSubscription(tenantId, planId));
        TenantContext.clear();

        assertThat(subscriptionRepository.existsByTenantId(tenantId)).isTrue();
    }

    @Test
    void subscription_existsByTenantId_returnsFalseForUnknownTenant() {
        assertThat(subscriptionRepository.existsByTenantId("sal_nobody_" + UUID.randomUUID())).isFalse();
    }

    @Test
    void subscription_findByTenantId_returnsEmptyForUnknownTenant() {
        Optional<SubscriptionJpaEntity> result = subscriptionRepository.findByTenantId(
                "sal_nobody_" + UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    // ── SubscriptionJpaRepository: unique constraints ─────────────────────

    @Test
    void subscription_uniqueTenantConstraint_rejectsSecondSubscriptionForSameTenant() {
        String tenantId = "sal_uniq_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Long planId = planRepository.findByName(PlanName.FREE_TRIAL).orElseThrow().getId();

        TenantContext.setCurrentTenantId(tenantId);
        subscriptionRepository.save(buildSubscription(tenantId, planId));
        TenantContext.clear();

        // Second subscription for the same tenant must violate the UNIQUE constraint on tenant_id
        TenantContext.setCurrentTenantId(tenantId);
        SubscriptionJpaEntity duplicate = buildSubscription(tenantId, planId);
        TenantContext.clear();

        assertThatThrownBy(() -> subscriptionRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void subscription_uniqueExternalIdConstraint_rejectsSecondSubscriptionWithSameExternalId() {
        String sharedExternalId = "sub_shared-ext-id-0000000000000000";
        Long planId = planRepository.findByName(PlanName.FREE_TRIAL).orElseThrow().getId();

        String tenant1 = "sal_ext1_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        TenantContext.setCurrentTenantId(tenant1);
        SubscriptionJpaEntity first = buildSubscription(tenant1, planId);
        first.setExternalId(sharedExternalId);
        subscriptionRepository.save(first);
        TenantContext.clear();

        String tenant2 = "sal_ext2_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        TenantContext.setCurrentTenantId(tenant2);
        SubscriptionJpaEntity second = buildSubscription(tenant2, planId);
        second.setExternalId(sharedExternalId); // same external_id → must fail
        TenantContext.clear();

        assertThatThrownBy(() -> subscriptionRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── SubscriptionJpaRepository: stripe fields ──────────────────────────

    @Test
    void subscription_findByStripeCustomerId_returnsSubscription() {
        String tenantId = "sal_stripe_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String customerId = "cus_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        Long planId = planRepository.findByName(PlanName.FREE_TRIAL).orElseThrow().getId();

        TenantContext.setCurrentTenantId(tenantId);
        SubscriptionJpaEntity entity = buildSubscription(tenantId, planId);
        entity.setStripeCustomerId(customerId);
        subscriptionRepository.save(entity);
        TenantContext.clear();

        Optional<SubscriptionJpaEntity> found = subscriptionRepository.findByStripeCustomerId(customerId);

        assertThat(found).isPresent();
        assertThat(found.get().getStripeCustomerId()).isEqualTo(customerId);
        assertThat(found.get().getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void subscription_findByStripeCustomerId_returnsEmptyForUnknownCustomer() {
        Optional<SubscriptionJpaEntity> result = subscriptionRepository.findByStripeCustomerId("cus_nobody");

        assertThat(result).isEmpty();
    }

    @Test
    void subscription_findByStripeSubscriptionId_returnsSubscription() {
        String tenantId = "sal_subi_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String stripeSubId = "sub_stripe_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Long planId = planRepository.findByName(PlanName.BASIC).orElseThrow().getId();

        TenantContext.setCurrentTenantId(tenantId);
        SubscriptionJpaEntity entity = buildSubscription(tenantId, planId);
        entity.setStripeSubscriptionId(stripeSubId);
        entity.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(entity);
        TenantContext.clear();

        Optional<SubscriptionJpaEntity> found = subscriptionRepository.findByStripeSubscriptionId(stripeSubId);

        assertThat(found).isPresent();
        assertThat(found.get().getStripeSubscriptionId()).isEqualTo(stripeSubId);
        assertThat(found.get().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    // ── SubscriptionJpaRepository: subscription status transitions ────────

    @Test
    void subscription_statusTransition_trialingToActive() {
        String tenantId = "sal_st_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Long planId = planRepository.findByName(PlanName.FREE_TRIAL).orElseThrow().getId();

        TenantContext.setCurrentTenantId(tenantId);
        SubscriptionJpaEntity entity = buildSubscription(tenantId, planId);
        SubscriptionJpaEntity saved = subscriptionRepository.save(entity);
        TenantContext.clear();

        assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.TRIALING);

        // Simulate webhook: invoice.paid → ACTIVE
        saved.setStatus(SubscriptionStatus.ACTIVE);
        Long basicPlanId = planRepository.findByName(PlanName.BASIC).orElseThrow().getId();
        saved.setPlanId(basicPlanId);
        SubscriptionJpaEntity updated = subscriptionRepository.save(saved);

        assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(updated.getPlanId()).isEqualTo(basicPlanId);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void subscription_cancelAtPeriodEnd_defaultIsFalse() {
        String tenantId = "sal_cancel_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Long planId = planRepository.findByName(PlanName.FREE_TRIAL).orElseThrow().getId();

        TenantContext.setCurrentTenantId(tenantId);
        SubscriptionJpaEntity saved = subscriptionRepository.save(buildSubscription(tenantId, planId));
        TenantContext.clear();

        assertThat(saved.isCancelAtPeriodEnd()).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Builds a minimal valid SubscriptionJpaEntity.
     * tenant_id is set explicitly here; TenantEntityListener will also set it
     * from TenantContext on @PrePersist if tenant_id is null — but setting it
     * directly makes the intent explicit and avoids ordering concerns.
     */
    private SubscriptionJpaEntity buildSubscription(String tenantId, Long planId) {
        SubscriptionJpaEntity entity = new SubscriptionJpaEntity();
        // UUID without dashes = 32 chars; "sub_" prefix = 4 chars; total = 36 — fits CHAR(44)
        entity.setExternalId("sub_" + UUID.randomUUID().toString().replace("-", ""));
        entity.setTenantId(tenantId);
        entity.setPlanId(planId);
        entity.setStatus(SubscriptionStatus.TRIALING);
        return entity;
    }

    /**
     * Finds the limit value for a given key, returning 0 if not found.
     * Tests that expect -1 (unlimited) must not use this default — they explicitly
     * call this method and assert the returned value equals -1.
     */
    private int findLimitValue(List<PlanLimitJpaEntity> limits, String key) {
        return limits.stream()
                .filter(l -> key.equals(l.getLimitKey()))
                .findFirst()
                .map(PlanLimitJpaEntity::getLimitValue)
                .orElse(0);
    }
}
