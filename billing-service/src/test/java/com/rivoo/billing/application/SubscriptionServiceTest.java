package com.rivoo.billing.application;

import com.rivoo.billing.application.dto.CreateSubscriptionRequest;
import com.rivoo.billing.application.dto.PlanResponse;
import com.rivoo.billing.application.dto.SubscriptionResponse;
import com.rivoo.billing.domain.exception.DuplicateSubscriptionException;
import com.rivoo.billing.domain.exception.PlanNotFoundException;
import com.rivoo.billing.domain.exception.SubscriptionNotFoundException;
import com.rivoo.billing.domain.model.PlanLimit;
import com.rivoo.billing.domain.model.PlanName;
import com.rivoo.billing.domain.model.Subscription;
import com.rivoo.billing.domain.model.SubscriptionPlan;
import com.rivoo.billing.domain.model.SubscriptionStatus;
import com.rivoo.billing.domain.port.out.AuthServicePort;
import com.rivoo.billing.domain.port.out.PlanLimitPersistencePort;
import com.rivoo.billing.domain.port.out.PlanPersistencePort;
import com.rivoo.billing.domain.port.out.StripePort;
import com.rivoo.billing.domain.port.out.SubscriptionPersistencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionPersistencePort subscriptionPersistencePort;

    @Mock
    private PlanPersistencePort planPersistencePort;

    @Mock
    private PlanLimitPersistencePort planLimitPersistencePort;

    @Mock
    private StripePort stripePort;

    @Mock
    private AuthServicePort authServicePort;

    @Mock
    private PlanLimitsService planLimitsService;

    private SubscriptionService subscriptionService;

    private static final String TENANT_ID = "sal_tenant-001";
    private static final String OWNER_EMAIL = "owner@salon.com";
    private static final String SALON_NAME = "Barberia Norte";
    private static final String STRIPE_CUSTOMER_ID = "cus_stripe123";
    private static final String STRIPE_SUBSCRIPTION_ID = "sub_stripe456";

    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionService(
                subscriptionPersistencePort, planPersistencePort, planLimitPersistencePort,
                stripePort, authServicePort, planLimitsService);
    }

    // ── create: FREE_TRIAL happy path ────────────────────────────────────

    @Test
    void create_freeTrial_setsStatusTrialingAndTrialEnd() {
        SubscriptionPlan freeTrial = buildFreeTrial();
        when(subscriptionPersistencePort.existsByTenantId(TENANT_ID)).thenReturn(false);
        when(planPersistencePort.findByName(PlanName.FREE_TRIAL)).thenReturn(Optional.of(freeTrial));
        when(stripePort.createCustomer(TENANT_ID, OWNER_EMAIL, SALON_NAME)).thenReturn(STRIPE_CUSTOMER_ID);
        when(subscriptionPersistencePort.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateSubscriptionRequest request = new CreateSubscriptionRequest(TENANT_ID, OWNER_EMAIL, SALON_NAME);
        SubscriptionResponse response = subscriptionService.create(request);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.TRIALING.name());
        assertThat(response.tenantId()).isEqualTo(TENANT_ID);
        assertThat(response.planName()).isEqualTo(PlanName.FREE_TRIAL.name());
        // FREE_TRIAL has a Stripe Customer but no Stripe Subscription yet
        assertThat(response.stripeCustomerId()).isEqualTo(STRIPE_CUSTOMER_ID);
        assertThat(response.stripeSubscriptionId()).isNull();

        // trialEnd must be 14 days after trialStart
        assertThat(response.trialEnd()).isAfter(response.trialStart());
        long daysBetween = java.time.Duration.between(response.trialStart(), response.trialEnd()).toDays();
        assertThat(daysBetween).isEqualTo(14L);
    }

    @Test
    void create_freeTrial_createsStripeCustomer() {
        when(subscriptionPersistencePort.existsByTenantId(TENANT_ID)).thenReturn(false);
        when(planPersistencePort.findByName(PlanName.FREE_TRIAL)).thenReturn(Optional.of(buildFreeTrial()));
        when(stripePort.createCustomer(TENANT_ID, OWNER_EMAIL, SALON_NAME)).thenReturn(STRIPE_CUSTOMER_ID);
        when(subscriptionPersistencePort.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        subscriptionService.create(new CreateSubscriptionRequest(TENANT_ID, OWNER_EMAIL, SALON_NAME));

        verify(stripePort).createCustomer(TENANT_ID, OWNER_EMAIL, SALON_NAME);
    }

    @Test
    void create_freeTrial_persistsSubscriptionWithStripeCustomerId() {
        when(subscriptionPersistencePort.existsByTenantId(TENANT_ID)).thenReturn(false);
        when(planPersistencePort.findByName(PlanName.FREE_TRIAL)).thenReturn(Optional.of(buildFreeTrial()));
        when(stripePort.createCustomer(TENANT_ID, OWNER_EMAIL, SALON_NAME)).thenReturn(STRIPE_CUSTOMER_ID);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        when(subscriptionPersistencePort.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        subscriptionService.create(new CreateSubscriptionRequest(TENANT_ID, OWNER_EMAIL, SALON_NAME));

        Subscription saved = captor.getValue();
        assertThat(saved.getStripeCustomerId()).isEqualTo(STRIPE_CUSTOMER_ID);
        assertThat(saved.getExternalId()).startsWith("sub_");
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
    }

    // ── getByTenantId: exposes the Stripe identifiers the frontend gates on ─

    @Test
    void getByTenantId_exposesStripeCustomerIdAndStripeSubscriptionId() {
        Subscription existing = buildSubscription(SubscriptionStatus.ACTIVE, PlanName.PREMIUM, 2L);
        existing.setStripeSubscriptionId(STRIPE_SUBSCRIPTION_ID);
        when(subscriptionPersistencePort.findByTenantId(TENANT_ID)).thenReturn(Optional.of(existing));
        when(planPersistencePort.findById(2L)).thenReturn(Optional.of(buildPlan(2L, PlanName.PREMIUM)));

        SubscriptionResponse response = subscriptionService.getByTenantId(TENANT_ID);

        // The billing settings page renders the "Gestionar suscripcion" button only when
        // stripeSubscriptionId is present, so both identifiers must survive the mapping.
        assertThat(response.stripeCustomerId()).isEqualTo(STRIPE_CUSTOMER_ID);
        assertThat(response.stripeSubscriptionId()).isEqualTo(STRIPE_SUBSCRIPTION_ID);
    }

    // ── create: duplicate tenant → throws ───────────────────────────────

    @Test
    void create_duplicateTenant_throwsDuplicateSubscriptionException() {
        when(subscriptionPersistencePort.existsByTenantId(TENANT_ID)).thenReturn(true);

        assertThatThrownBy(() ->
                subscriptionService.create(new CreateSubscriptionRequest(TENANT_ID, OWNER_EMAIL, SALON_NAME)))
                .isInstanceOf(DuplicateSubscriptionException.class);

        verify(stripePort, never()).createCustomer(anyString(), anyString(), anyString());
        verify(subscriptionPersistencePort, never()).save(any());
    }

    // ── create: FREE_TRIAL plan missing → throws ─────────────────────────

    @Test
    void create_freeTialPlanNotFound_throwsPlanNotFoundException() {
        when(subscriptionPersistencePort.existsByTenantId(TENANT_ID)).thenReturn(false);
        when(planPersistencePort.findByName(PlanName.FREE_TRIAL)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                subscriptionService.create(new CreateSubscriptionRequest(TENANT_ID, OWNER_EMAIL, SALON_NAME)))
                .isInstanceOf(PlanNotFoundException.class);
    }

    // ── upgradePlan: updates plan, syncs Keycloak, evicts cache ─────────

    @Test
    void upgradePlan_updatesPlanAndSetsActiveStatus() {
        Subscription existing = buildSubscription(SubscriptionStatus.TRIALING, PlanName.FREE_TRIAL, 1L);
        SubscriptionPlan premiumPlan = buildPlan(2L, PlanName.PREMIUM);

        when(subscriptionPersistencePort.findByTenantId(TENANT_ID)).thenReturn(Optional.of(existing));
        when(planPersistencePort.findByName(PlanName.PREMIUM)).thenReturn(Optional.of(premiumPlan));
        when(subscriptionPersistencePort.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        subscriptionService.upgradePlan(TENANT_ID, PlanName.PREMIUM);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionPersistencePort).save(captor.capture());
        Subscription saved = captor.getValue();

        assertThat(saved.getPlanId()).isEqualTo(2L);
        assertThat(saved.getPlanName()).isEqualTo(PlanName.PREMIUM);
        assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(saved.getCurrentPeriodEnd()).isAfter(Instant.now().minusSeconds(10));
    }

    @Test
    void upgradePlan_syncsNewPlanToKeycloak() {
        when(subscriptionPersistencePort.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(buildSubscription(SubscriptionStatus.TRIALING, PlanName.FREE_TRIAL, 1L)));
        when(planPersistencePort.findByName(PlanName.PREMIUM)).thenReturn(Optional.of(buildPlan(2L, PlanName.PREMIUM)));
        when(subscriptionPersistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        subscriptionService.upgradePlan(TENANT_ID, PlanName.PREMIUM);

        verify(authServicePort).updateTenantAttributes(eq(TENANT_ID),
                eq(Map.of("subscription_plan", PlanName.PREMIUM.name())));
    }

    @Test
    void upgradePlan_evictsPlanLimitsCache() {
        when(subscriptionPersistencePort.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(buildSubscription(SubscriptionStatus.TRIALING, PlanName.FREE_TRIAL, 1L)));
        when(planPersistencePort.findByName(PlanName.PREMIUM)).thenReturn(Optional.of(buildPlan(2L, PlanName.PREMIUM)));
        when(subscriptionPersistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        subscriptionService.upgradePlan(TENANT_ID, PlanName.PREMIUM);

        verify(planLimitsService).evictCache(TENANT_ID);
    }

    @Test
    void upgradePlan_keycloakSyncFails_subscriptionStillUpdated() {
        when(subscriptionPersistencePort.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(buildSubscription(SubscriptionStatus.TRIALING, PlanName.FREE_TRIAL, 1L)));
        when(planPersistencePort.findByName(PlanName.PREMIUM)).thenReturn(Optional.of(buildPlan(2L, PlanName.PREMIUM)));
        when(subscriptionPersistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Keycloak unreachable"))
                .when(authServicePort).updateTenantAttributes(anyString(), any());

        // Should not throw — Keycloak sync failure is logged and swallowed
        subscriptionService.upgradePlan(TENANT_ID, PlanName.PREMIUM);

        // Cache still evicted even after Keycloak failure
        verify(planLimitsService).evictCache(TENANT_ID);
    }

    @Test
    void upgradePlan_tenantNotFound_throwsSubscriptionNotFoundException() {
        when(subscriptionPersistencePort.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.upgradePlan(TENANT_ID, PlanName.PREMIUM))
                .isInstanceOf(SubscriptionNotFoundException.class);
    }

    // ── listActivePlans: the anonymous plan catalogue ────────────────

    @Test
    void listActivePlans_returnsEveryActivePlanWithItsOwnLimits() {
        SubscriptionPlan basic = buildPlan(2L, PlanName.BASIC);
        SubscriptionPlan premium = buildPlan(3L, PlanName.PREMIUM);
        when(planPersistencePort.findAllActive()).thenReturn(List.of(basic, premium));
        when(planLimitPersistencePort.findByPlanIds(List.of(2L, 3L)))
                .thenReturn(concat(fullLimits(2L, 3, 200, 1, 0), fullLimits(3L, 10, -1, 1, 1)));

        List<PlanResponse> plans = subscriptionService.listActivePlans();

        assertThat(plans).hasSize(2);

        // Each plan must get ITS OWN rows, not the first plan's rows repeated: the limits
        // come from a single bulk query joined in memory, and a wrong grouping key would
        // hand every entry the same block.
        assertThat(plans.getFirst().name()).isEqualTo("BASIC");
        assertThat(plans.getFirst().limits().maxEmployees()).isEqualTo(3);
        assertThat(plans.getFirst().limits().maxAppointmentsPerMonth()).isEqualTo(200);
        assertThat(plans.getFirst().limits().emailRemindersEnabled()).isTrue();
        assertThat(plans.getFirst().limits().smsRemindersEnabled()).isFalse();

        assertThat(plans.getLast().name()).isEqualTo("PREMIUM");
        assertThat(plans.getLast().limits().maxEmployees()).isEqualTo(10);
        assertThat(plans.getLast().limits().maxAppointmentsPerMonth()).isEqualTo(-1);
        assertThat(plans.getLast().limits().emailRemindersEnabled()).isTrue();
        assertThat(plans.getLast().limits().smsRemindersEnabled()).isTrue();
    }

    @Test
    void listActivePlans_stillCarriesTheOriginalPricingFields() {
        SubscriptionPlan freeTrial = buildFreeTrial();
        when(planPersistencePort.findAllActive()).thenReturn(List.of(freeTrial));
        when(planLimitPersistencePort.findByPlanIds(List.of(1L)))
                .thenReturn(fullLimits(1L, 1, 50, 0, 0));

        PlanResponse plan = subscriptionService.listActivePlans().getFirst();

        // The four fields rivoo-frontend/src/types/billing.ts:PlanInfo declares, plus
        // trialDays. Adding `limits` must not disturb any of them.
        assertThat(plan.id()).isEqualTo("pln_freetrial");
        assertThat(plan.name()).isEqualTo("FREE_TRIAL");
        assertThat(plan.displayName()).isEqualTo("Free Trial");
        assertThat(plan.monthlyPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(plan.trialDays()).isEqualTo(14);
    }

    @Test
    void listActivePlans_planMissingALimitRow_reportsNullNotMinusOneNorZero() {
        SubscriptionPlan basic = buildPlan(2L, PlanName.BASIC);
        when(planPersistencePort.findAllActive()).thenReturn(List.of(basic));
        // No max_employees row and no sms_reminders_enabled row for this plan.
        when(planLimitPersistencePort.findByPlanIds(List.of(2L))).thenReturn(List.of(
                limit(2L, "max_appointments_per_month", 200),
                limit(2L, "email_reminders_enabled", 1)));

        PlanResponse plan = subscriptionService.listActivePlans().getFirst();

        // null = "unspecified", which rules out both alternatives at once: -1, which means
        // "unlimited" everywhere in this schema, and 0/false, which means
        // "none"/"disabled". A misconfigured plan must not advertise an unlimited
        // headcount on the public pricing page, nor an SMS feature it may well include.
        assertThat(plan.limits().maxEmployees()).isNull();
        assertThat(plan.limits().smsRemindersEnabled()).isNull();

        // The rows that DO exist are unaffected.
        assertThat(plan.limits().maxAppointmentsPerMonth()).isEqualTo(200);
        assertThat(plan.limits().emailRemindersEnabled()).isTrue();
    }

    @Test
    void listActivePlans_planWithNoLimitRowsAtAll_stillReturnsTheEntry() {
        SubscriptionPlan enterprise = buildPlan(4L, PlanName.ENTERPRISE);
        when(planPersistencePort.findAllActive()).thenReturn(List.of(enterprise));
        when(planLimitPersistencePort.findByPlanIds(List.of(4L))).thenReturn(List.of());

        PlanResponse plan = subscriptionService.listActivePlans().getFirst();

        // A plan with no configured limits is still a purchasable tier: it must not vanish
        // from the catalogue, and `limits` must be an object rather than null so the
        // frontend can read through it without a guard.
        assertThat(plan.name()).isEqualTo("ENTERPRISE");
        assertThat(plan.limits()).isNotNull();
        assertThat(plan.limits().maxEmployees()).isNull();
        assertThat(plan.limits().maxAppointmentsPerMonth()).isNull();
        assertThat(plan.limits().emailRemindersEnabled()).isNull();
        assertThat(plan.limits().smsRemindersEnabled()).isNull();
    }

    @Test
    void listActivePlans_fetchesAllLimitsInOneQuery_notOnePerPlan() {
        SubscriptionPlan basic = buildPlan(2L, PlanName.BASIC);
        SubscriptionPlan premium = buildPlan(3L, PlanName.PREMIUM);
        SubscriptionPlan enterprise = buildPlan(4L, PlanName.ENTERPRISE);
        when(planPersistencePort.findAllActive()).thenReturn(List.of(basic, premium, enterprise));
        when(planLimitPersistencePort.findByPlanIds(List.of(2L, 3L, 4L))).thenReturn(List.of());

        subscriptionService.listActivePlans();

        // N+1 guard on an endpoint anyone on the internet can call in a loop: exactly one
        // bulk call, and never the per-plan accessor. Mockito's default answer for an
        // unstubbed findByPlanId is an empty list, so a naive per-plan loop would satisfy
        // every other assertion in this class and fail only here.
        verify(planLimitPersistencePort).findByPlanIds(List.of(2L, 3L, 4L));
        verify(planLimitPersistencePort, never()).findByPlanId(any());
        verifyNoMoreInteractions(planLimitPersistencePort);
    }

    @Test
    void listActivePlans_noActivePlans_doesNotQueryLimitsAtAll() {
        when(planPersistencePort.findAllActive()).thenReturn(List.of());

        assertThat(subscriptionService.listActivePlans()).isEmpty();

        // An empty IN () list is a query that cannot match anything; skip it.
        verifyNoInteractions(planLimitPersistencePort);
    }

    @Test
    void listActivePlans_readsNothingTenantScoped() {
        SubscriptionPlan basic = buildPlan(2L, PlanName.BASIC);
        when(planPersistencePort.findAllActive()).thenReturn(List.of(basic));
        when(planLimitPersistencePort.findByPlanIds(List.of(2L)))
                .thenReturn(fullLimits(2L, 3, 200, 1, 0));

        subscriptionService.listActivePlans();

        // The endpoint is anonymous, so there is no tenant in scope: reaching the
        // subscription store from here could only mean serving somebody else's data.
        verifyNoInteractions(subscriptionPersistencePort, planLimitsService, stripePort, authServicePort);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private SubscriptionPlan buildFreeTrial() {
        return SubscriptionPlan.builder()
                .id(1L)
                .externalId("pln_freetrial")
                .name(PlanName.FREE_TRIAL)
                .displayName("Free Trial")
                .monthlyPrice(BigDecimal.ZERO)
                .trialDays(14)
                .active(true)
                .build();
    }

    private SubscriptionPlan buildPlan(Long id, PlanName name) {
        return SubscriptionPlan.builder()
                .id(id)
                .externalId("pln_" + name.name().toLowerCase())
                .name(name)
                .displayName(name.name())
                .monthlyPrice(BigDecimal.valueOf(29))
                .trialDays(0)
                .active(true)
                .build();
    }

    private Subscription buildSubscription(SubscriptionStatus status, PlanName planName, Long planId) {
        return Subscription.builder()
                .id(10L)
                .externalId("sub_abc123")
                .tenantId(TENANT_ID)
                .planId(planId)
                .planName(planName)
                .status(status)
                .stripeCustomerId(STRIPE_CUSTOMER_ID)
                .trialStart(Instant.now().minusSeconds(86400))
                .trialEnd(Instant.now().plusSeconds(86400 * 13))
                .build();
    }

    private PlanLimit limit(Long planId, String key, int value) {
        return PlanLimit.builder().planId(planId).limitKey(key).limitValue(value).build();
    }

    private List<PlanLimit> fullLimits(Long planId, int employees, int appointments, int email, int sms) {
        return List.of(
                limit(planId, "max_employees", employees),
                limit(planId, "max_appointments_per_month", appointments),
                limit(planId, "email_reminders_enabled", email),
                limit(planId, "sms_reminders_enabled", sms));
    }

    private List<PlanLimit> concat(List<PlanLimit> first, List<PlanLimit> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }
}
