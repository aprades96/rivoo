package com.rivoo.billing.application;

import com.rivoo.billing.application.dto.CreateSubscriptionRequest;
import com.rivoo.billing.application.dto.SubscriptionResponse;
import com.rivoo.billing.domain.exception.DuplicateSubscriptionException;
import com.rivoo.billing.domain.exception.PlanNotFoundException;
import com.rivoo.billing.domain.exception.SubscriptionNotFoundException;
import com.rivoo.billing.domain.model.PlanName;
import com.rivoo.billing.domain.model.Subscription;
import com.rivoo.billing.domain.model.SubscriptionPlan;
import com.rivoo.billing.domain.model.SubscriptionStatus;
import com.rivoo.billing.domain.port.out.AuthServicePort;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionPersistencePort subscriptionPersistencePort;

    @Mock
    private PlanPersistencePort planPersistencePort;

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

    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionService(
                subscriptionPersistencePort, planPersistencePort,
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
}
