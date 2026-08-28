package com.rivoo.billing.application;

import com.rivoo.billing.application.dto.PlanLimitsResponse;
import com.rivoo.billing.domain.exception.SubscriptionNotFoundException;
import com.rivoo.billing.domain.model.PlanLimit;
import com.rivoo.billing.domain.model.PlanName;
import com.rivoo.billing.domain.model.Subscription;
import com.rivoo.billing.domain.model.SubscriptionPlan;
import com.rivoo.billing.domain.model.SubscriptionStatus;
import com.rivoo.billing.domain.port.out.PlanLimitPersistencePort;
import com.rivoo.billing.domain.port.out.PlanPersistencePort;
import com.rivoo.billing.domain.port.out.SubscriptionPersistencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanLimitsServiceTest {

    @Mock
    private SubscriptionPersistencePort subscriptionPersistencePort;

    @Mock
    private PlanPersistencePort planPersistencePort;

    @Mock
    private PlanLimitPersistencePort planLimitPersistencePort;

    // PlanLimitsService builds its own Caffeine cache internally, so we instantiate it directly
    private PlanLimitsService planLimitsService;

    private static final String TENANT_ID = "sal_test-tenant-001";
    private static final Long PLAN_ID = 1L;

    @BeforeEach
    void setUp() {
        planLimitsService = new PlanLimitsService(
                subscriptionPersistencePort, planPersistencePort, planLimitPersistencePort);
    }

    // ── Cache hit returns cached value without hitting DB ────────────────

    @Test
    void getPlanLimits_cacheHit_doesNotHitDatabase() {
        // Warm up the cache with one real DB call
        Subscription subscription = buildSubscription(TENANT_ID, PLAN_ID);
        SubscriptionPlan plan = buildPlan(PLAN_ID, PlanName.BASIC);
        List<PlanLimit> limits = basicLimits(PLAN_ID);

        when(subscriptionPersistencePort.findByTenantId(TENANT_ID)).thenReturn(Optional.of(subscription));
        when(planPersistencePort.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(planLimitPersistencePort.findByPlanId(PLAN_ID)).thenReturn(limits);

        planLimitsService.getPlanLimits(TENANT_ID, false); // fills cache

        // Second call — should return cached result without DB access
        PlanLimitsResponse result = planLimitsService.getPlanLimits(TENANT_ID, false);

        assertThat(result).isNotNull();
        assertThat(result.planName()).isEqualTo("BASIC");
        // Each port should have been invoked exactly once (from the first call only)
        verify(subscriptionPersistencePort).findByTenantId(TENANT_ID);
        verify(planPersistencePort).findById(PLAN_ID);
        verify(planLimitPersistencePort).findByPlanId(PLAN_ID);
    }

    // ── forWriteOperation=true always bypasses cache ─────────────────────

    @Test
    void getPlanLimits_forWriteOperation_alwaysBypassesCache() {
        // Warm up the cache
        Subscription subscription = buildSubscription(TENANT_ID, PLAN_ID);
        SubscriptionPlan plan = buildPlan(PLAN_ID, PlanName.PREMIUM);
        List<PlanLimit> limits = premiumLimits(PLAN_ID);

        when(subscriptionPersistencePort.findByTenantId(TENANT_ID)).thenReturn(Optional.of(subscription));
        when(planPersistencePort.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(planLimitPersistencePort.findByPlanId(PLAN_ID)).thenReturn(limits);

        planLimitsService.getPlanLimits(TENANT_ID, false); // fills cache

        // Write-operation call — must go to DB again
        planLimitsService.getPlanLimits(TENANT_ID, true);

        // Each port invoked twice: once for cache warm-up, once for write-op bypass
        verify(subscriptionPersistencePort, org.mockito.Mockito.times(2)).findByTenantId(TENANT_ID);
        verify(planPersistencePort, org.mockito.Mockito.times(2)).findById(PLAN_ID);
        verify(planLimitPersistencePort, org.mockito.Mockito.times(2)).findByPlanId(PLAN_ID);
    }

    // ── Correct limit values returned ────────────────────────────────────

    @Test
    void getPlanLimits_basicPlan_returnsCorrectLimitValues() {
        Subscription subscription = buildSubscription(TENANT_ID, PLAN_ID);
        SubscriptionPlan plan = buildPlan(PLAN_ID, PlanName.BASIC);
        List<PlanLimit> limits = basicLimits(PLAN_ID);

        when(subscriptionPersistencePort.findByTenantId(TENANT_ID)).thenReturn(Optional.of(subscription));
        when(planPersistencePort.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(planLimitPersistencePort.findByPlanId(PLAN_ID)).thenReturn(limits);

        PlanLimitsResponse result = planLimitsService.getPlanLimits(TENANT_ID, false);

        assertThat(result.planName()).isEqualTo("BASIC");
        assertThat(result.maxEmployees()).isEqualTo(3);
        assertThat(result.maxAppointmentsPerMonth()).isEqualTo(200);
        assertThat(result.emailRemindersEnabled()).isTrue();
        assertThat(result.smsRemindersEnabled()).isFalse();
    }

    @Test
    void getPlanLimits_enterprisePlan_returnsUnlimitedValues() {
        Subscription subscription = buildSubscription(TENANT_ID, PLAN_ID);
        SubscriptionPlan plan = buildPlan(PLAN_ID, PlanName.ENTERPRISE);
        List<PlanLimit> limits = enterpriseLimits(PLAN_ID);

        when(subscriptionPersistencePort.findByTenantId(TENANT_ID)).thenReturn(Optional.of(subscription));
        when(planPersistencePort.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(planLimitPersistencePort.findByPlanId(PLAN_ID)).thenReturn(limits);

        PlanLimitsResponse result = planLimitsService.getPlanLimits(TENANT_ID, false);

        assertThat(result.maxEmployees()).isEqualTo(-1);       // unlimited
        assertThat(result.maxAppointmentsPerMonth()).isEqualTo(-1);
        assertThat(result.emailRemindersEnabled()).isTrue();
        assertThat(result.smsRemindersEnabled()).isTrue();
    }

    // ── Missing plan_limits rows keep the enforcement-side defaults ─────

    @Test
    void getPlanLimits_missingRows_keepsTheEnforcementDefaults() {
        Subscription subscription = buildSubscription(TENANT_ID, PLAN_ID);
        SubscriptionPlan plan = buildPlan(PLAN_ID, PlanName.BASIC);

        when(subscriptionPersistencePort.findByTenantId(TENANT_ID)).thenReturn(Optional.of(subscription));
        when(planPersistencePort.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(planLimitPersistencePort.findByPlanId(PLAN_ID)).thenReturn(List.of());

        PlanLimitsResponse result = planLimitsService.getPlanLimits(TENANT_ID, false);

        // Characterisation test, pinning behaviour that predates the extraction of
        // PlanLimits: on this INTERNAL endpoint a missing quota row still reads as -1
        // (unlimited) and a missing flag as false. appointment-service and staff-service
        // enforce against these values through a primitive-typed DTO, so this is the shape
        // they have always been served and it is deliberately left unchanged here.
        //
        // The anonymous catalogue in SubscriptionService#listActivePlans makes the opposite
        // choice (absent stays null). Both go through PlanLimits.from, so only the default
        // policy differs -- never the key names or the int-to-boolean encoding. If someone
        // "unifies" the two policies, this test and
        // SubscriptionServiceTest#listActivePlans_planMissingALimitRow_reportsNullNotMinusOneNorZero
        // cannot both stay green.
        assertThat(result.planName()).isEqualTo("BASIC");
        assertThat(result.maxEmployees()).isEqualTo(-1);
        assertThat(result.maxAppointmentsPerMonth()).isEqualTo(-1);
        assertThat(result.emailRemindersEnabled()).isFalse();
        assertThat(result.smsRemindersEnabled()).isFalse();
    }

    @Test
    void getPlanLimits_partialRows_defaultsOnlyTheMissingOnes() {
        Subscription subscription = buildSubscription(TENANT_ID, PLAN_ID);
        SubscriptionPlan plan = buildPlan(PLAN_ID, PlanName.BASIC);

        when(subscriptionPersistencePort.findByTenantId(TENANT_ID)).thenReturn(Optional.of(subscription));
        when(planPersistencePort.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(planLimitPersistencePort.findByPlanId(PLAN_ID)).thenReturn(List.of(
                PlanLimit.builder().planId(PLAN_ID).limitKey("max_employees").limitValue(3).build(),
                PlanLimit.builder().planId(PLAN_ID).limitKey("sms_reminders_enabled").limitValue(1).build()));

        PlanLimitsResponse result = planLimitsService.getPlanLimits(TENANT_ID, false);

        assertThat(result.maxEmployees()).isEqualTo(3);
        assertThat(result.smsRemindersEnabled()).isTrue();
        assertThat(result.maxAppointmentsPerMonth()).isEqualTo(-1);
        assertThat(result.emailRemindersEnabled()).isFalse();
    }

    // ── Subscription not found → throws ─────────────────────────────────

    @Test
    void getPlanLimits_subscriptionNotFound_throwsSubscriptionNotFoundException() {
        when(subscriptionPersistencePort.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> planLimitsService.getPlanLimits(TENANT_ID, false))
                .isInstanceOf(SubscriptionNotFoundException.class);

        verify(planPersistencePort, never()).findById(anyLong());
        verify(planLimitPersistencePort, never()).findByPlanId(anyLong());
    }

    // ── evictCache removes entry so next read goes to DB ────────────────

    @Test
    void evictCache_removesEntryFromCache() {
        Subscription subscription = buildSubscription(TENANT_ID, PLAN_ID);
        SubscriptionPlan plan = buildPlan(PLAN_ID, PlanName.BASIC);
        List<PlanLimit> limits = basicLimits(PLAN_ID);

        when(subscriptionPersistencePort.findByTenantId(TENANT_ID)).thenReturn(Optional.of(subscription));
        when(planPersistencePort.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(planLimitPersistencePort.findByPlanId(PLAN_ID)).thenReturn(limits);

        planLimitsService.getPlanLimits(TENANT_ID, false); // fills cache
        planLimitsService.evictCache(TENANT_ID);
        planLimitsService.getPlanLimits(TENANT_ID, false); // must re-fetch from DB

        verify(subscriptionPersistencePort, org.mockito.Mockito.times(2)).findByTenantId(TENANT_ID);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Subscription buildSubscription(String tenantId, Long planId) {
        return Subscription.builder()
                .id(1L)
                .externalId("sub_abc")
                .tenantId(tenantId)
                .planId(planId)
                .planName(PlanName.BASIC)
                .status(SubscriptionStatus.ACTIVE)
                .build();
    }

    private SubscriptionPlan buildPlan(Long id, PlanName name) {
        return SubscriptionPlan.builder()
                .id(id)
                .externalId("pln_abc")
                .name(name)
                .displayName(name.name())
                .monthlyPrice(BigDecimal.valueOf(29))
                .trialDays(0)
                .active(true)
                .build();
    }

    private List<PlanLimit> basicLimits(Long planId) {
        return List.of(
                PlanLimit.builder().planId(planId).limitKey("max_employees").limitValue(3).build(),
                PlanLimit.builder().planId(planId).limitKey("max_appointments_per_month").limitValue(200).build(),
                PlanLimit.builder().planId(planId).limitKey("email_reminders_enabled").limitValue(1).build(),
                PlanLimit.builder().planId(planId).limitKey("sms_reminders_enabled").limitValue(0).build()
        );
    }

    private List<PlanLimit> premiumLimits(Long planId) {
        return List.of(
                PlanLimit.builder().planId(planId).limitKey("max_employees").limitValue(10).build(),
                PlanLimit.builder().planId(planId).limitKey("max_appointments_per_month").limitValue(-1).build(),
                PlanLimit.builder().planId(planId).limitKey("email_reminders_enabled").limitValue(1).build(),
                PlanLimit.builder().planId(planId).limitKey("sms_reminders_enabled").limitValue(1).build()
        );
    }

    private List<PlanLimit> enterpriseLimits(Long planId) {
        return List.of(
                PlanLimit.builder().planId(planId).limitKey("max_employees").limitValue(-1).build(),
                PlanLimit.builder().planId(planId).limitKey("max_appointments_per_month").limitValue(-1).build(),
                PlanLimit.builder().planId(planId).limitKey("email_reminders_enabled").limitValue(1).build(),
                PlanLimit.builder().planId(planId).limitKey("sms_reminders_enabled").limitValue(1).build()
        );
    }
}
