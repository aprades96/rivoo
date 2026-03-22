package com.rivoo.billing.application;

import com.rivoo.billing.domain.model.PlanName;
import com.rivoo.billing.domain.model.Subscription;
import com.rivoo.billing.domain.model.SubscriptionPlan;
import com.rivoo.billing.domain.model.SubscriptionStatus;
import com.rivoo.billing.domain.model.WebhookEvent;
import com.rivoo.billing.domain.port.out.PlanPersistencePort;
import com.rivoo.billing.domain.port.out.StripePort;
import com.rivoo.billing.domain.port.out.StripePort.StripeWebhookEvent;
import com.rivoo.billing.domain.port.out.SubscriptionPersistencePort;
import com.rivoo.billing.domain.port.out.WebhookEventPersistencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private WebhookEventPersistencePort webhookEventPersistencePort;

    @Mock
    private SubscriptionPersistencePort subscriptionPersistencePort;

    @Mock
    private PlanPersistencePort planPersistencePort;

    @Mock
    private StripePort stripePort;

    @Mock
    private SubscriptionService subscriptionService;

    private WebhookService webhookService;

    private static final String PAYLOAD = "{\"id\":\"evt_123\"}";
    private static final String SIGNATURE = "t=1,v1=abc";
    private static final String EVENT_ID = "evt_123";
    private static final String CUSTOMER_ID = "cus_stripe001";
    private static final String STRIPE_SUB_ID = "sub_stripe001";
    private static final String TENANT_ID = "sal_tenant-001";

    @BeforeEach
    void setUp() {
        webhookService = new WebhookService(
                webhookEventPersistencePort, subscriptionPersistencePort,
                planPersistencePort, stripePort, subscriptionService);
    }

    // ── Invalid signature → skip processing ──────────────────────────────

    @Test
    void processEvent_invalidSignature_returnsWithoutProcessing() {
        when(stripePort.constructEvent(PAYLOAD, SIGNATURE)).thenReturn(null);

        webhookService.processEvent(PAYLOAD, SIGNATURE);

        verify(webhookEventPersistencePort, never()).existsByStripeEventId(anyString());
        verify(webhookEventPersistencePort, never()).save(any());
        verify(subscriptionPersistencePort, never()).findByStripeCustomerId(anyString());
    }

    // ── Idempotency: duplicate eventId → skips processing ────────────────

    @Test
    void processEvent_duplicateEventId_skipsAllProcessing() {
        StripeWebhookEvent event = new StripeWebhookEvent(EVENT_ID, "checkout.session.completed",
                CUSTOMER_ID, STRIPE_SUB_ID, null);
        when(stripePort.constructEvent(PAYLOAD, SIGNATURE)).thenReturn(event);
        when(webhookEventPersistencePort.existsByStripeEventId(EVENT_ID)).thenReturn(true);

        webhookService.processEvent(PAYLOAD, SIGNATURE);

        verify(subscriptionPersistencePort, never()).findByStripeCustomerId(anyString());
        verify(subscriptionPersistencePort, never()).findByStripeSubscriptionId(anyString());
        verify(webhookEventPersistencePort, never()).save(any());
    }

    // ── checkout.session.completed → links subscription ──────────────────

    @Test
    void processEvent_checkoutSessionCompleted_linksStripeSubscriptionAndActivates() {
        String priceId = "price_basic_monthly";
        StripeWebhookEvent event = new StripeWebhookEvent(EVENT_ID, "checkout.session.completed",
                CUSTOMER_ID, STRIPE_SUB_ID, priceId);

        Subscription subscription = buildSubscription(SubscriptionStatus.TRIALING, 1L);
        SubscriptionPlan basicPlan = buildPlan(2L, PlanName.BASIC, priceId);

        when(stripePort.constructEvent(PAYLOAD, SIGNATURE)).thenReturn(event);
        when(webhookEventPersistencePort.existsByStripeEventId(EVENT_ID)).thenReturn(false);
        when(subscriptionPersistencePort.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(subscription));
        when(planPersistencePort.findAllActive()).thenReturn(List.of(basicPlan));
        when(subscriptionPersistencePort.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(webhookEventPersistencePort.save(any(WebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        webhookService.processEvent(PAYLOAD, SIGNATURE);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionPersistencePort).save(captor.capture());
        Subscription saved = captor.getValue();

        assertThat(saved.getStripeSubscriptionId()).isEqualTo(STRIPE_SUB_ID);
        assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void processEvent_checkoutSessionCompleted_callsUpgradePlanWithMatchedPlan() {
        String priceId = "price_premium_monthly";
        StripeWebhookEvent event = new StripeWebhookEvent(EVENT_ID, "checkout.session.completed",
                CUSTOMER_ID, STRIPE_SUB_ID, priceId);

        Subscription subscription = buildSubscription(SubscriptionStatus.TRIALING, 1L);
        SubscriptionPlan premiumPlan = buildPlan(2L, PlanName.PREMIUM, priceId);

        when(stripePort.constructEvent(PAYLOAD, SIGNATURE)).thenReturn(event);
        when(webhookEventPersistencePort.existsByStripeEventId(EVENT_ID)).thenReturn(false);
        when(subscriptionPersistencePort.findByStripeCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(subscription));
        when(planPersistencePort.findAllActive()).thenReturn(List.of(premiumPlan));
        when(subscriptionPersistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(webhookEventPersistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        webhookService.processEvent(PAYLOAD, SIGNATURE);

        verify(subscriptionService).upgradePlan(TENANT_ID, PlanName.PREMIUM);
    }

    // ── invoice.payment_failed → status=PAST_DUE ────────────────────────

    @Test
    void processEvent_invoicePaymentFailed_setsStatusPastDue() {
        StripeWebhookEvent event = new StripeWebhookEvent(EVENT_ID, "invoice.payment_failed",
                CUSTOMER_ID, STRIPE_SUB_ID, null);

        Subscription subscription = buildSubscription(SubscriptionStatus.ACTIVE, 2L);

        when(stripePort.constructEvent(PAYLOAD, SIGNATURE)).thenReturn(event);
        when(webhookEventPersistencePort.existsByStripeEventId(EVENT_ID)).thenReturn(false);
        when(subscriptionPersistencePort.findByStripeSubscriptionId(STRIPE_SUB_ID))
                .thenReturn(Optional.of(subscription));
        when(subscriptionPersistencePort.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(webhookEventPersistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        webhookService.processEvent(PAYLOAD, SIGNATURE);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionPersistencePort).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    // ── invoice.paid → status=ACTIVE ────────────────────────────────────

    @Test
    void processEvent_invoicePaid_setsStatusActive() {
        StripeWebhookEvent event = new StripeWebhookEvent(EVENT_ID, "invoice.paid",
                CUSTOMER_ID, STRIPE_SUB_ID, null);

        Subscription subscription = buildSubscription(SubscriptionStatus.PAST_DUE, 2L);

        when(stripePort.constructEvent(PAYLOAD, SIGNATURE)).thenReturn(event);
        when(webhookEventPersistencePort.existsByStripeEventId(EVENT_ID)).thenReturn(false);
        when(subscriptionPersistencePort.findByStripeSubscriptionId(STRIPE_SUB_ID))
                .thenReturn(Optional.of(subscription));
        when(subscriptionPersistencePort.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(webhookEventPersistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        webhookService.processEvent(PAYLOAD, SIGNATURE);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionPersistencePort).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    // ── Processed events are always logged ──────────────────────────────

    @Test
    void processEvent_validEvent_savesWebhookEventLog() {
        StripeWebhookEvent event = new StripeWebhookEvent(EVENT_ID, "customer.subscription.updated",
                CUSTOMER_ID, STRIPE_SUB_ID, null);

        when(stripePort.constructEvent(PAYLOAD, SIGNATURE)).thenReturn(event);
        when(webhookEventPersistencePort.existsByStripeEventId(EVENT_ID)).thenReturn(false);
        when(webhookEventPersistencePort.save(any(WebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        webhookService.processEvent(PAYLOAD, SIGNATURE);

        ArgumentCaptor<WebhookEvent> captor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventPersistencePort).save(captor.capture());
        WebhookEvent logged = captor.getValue();

        assertThat(logged.getStripeEventId()).isEqualTo(EVENT_ID);
        assertThat(logged.getEventType()).isEqualTo("customer.subscription.updated");
        assertThat(logged.getProcessedAt()).isNotNull();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Subscription buildSubscription(SubscriptionStatus status, Long planId) {
        return Subscription.builder()
                .id(10L)
                .externalId("sub_abc")
                .tenantId(TENANT_ID)
                .planId(planId)
                .status(status)
                .stripeCustomerId(CUSTOMER_ID)
                .stripeSubscriptionId(STRIPE_SUB_ID)
                .build();
    }

    private SubscriptionPlan buildPlan(Long id, PlanName name, String priceId) {
        return SubscriptionPlan.builder()
                .id(id)
                .externalId("pln_" + name.name().toLowerCase())
                .name(name)
                .displayName(name.name())
                .monthlyPrice(BigDecimal.valueOf(29))
                .stripeMonthlyPriceId(priceId)
                .active(true)
                .build();
    }
}
