package com.rivoo.billing.application;

import com.rivoo.billing.domain.model.PlanName;
import com.rivoo.billing.domain.model.Subscription;
import com.rivoo.billing.domain.model.SubscriptionStatus;
import com.rivoo.billing.domain.model.WebhookEvent;
import com.rivoo.billing.domain.port.in.ProcessWebhookUseCase;
import com.rivoo.billing.domain.port.out.PlanPersistencePort;
import com.rivoo.billing.domain.port.out.StripePort;
import com.rivoo.billing.domain.port.out.SubscriptionPersistencePort;
import com.rivoo.billing.domain.port.out.WebhookEventPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService implements ProcessWebhookUseCase {

    private final WebhookEventPersistencePort webhookEventPersistencePort;
    private final SubscriptionPersistencePort subscriptionPersistencePort;
    private final PlanPersistencePort planPersistencePort;
    private final StripePort stripePort;
    private final SubscriptionService subscriptionService;

    @Override
    @Transactional
    public void processEvent(String payload, String signatureHeader) {
        StripePort.StripeWebhookEvent event = stripePort.constructEvent(payload, signatureHeader);
        if (event == null) {
            log.atWarn().log("Invalid webhook signature — ignoring");
            return;
        }

        // Idempotency check
        if (webhookEventPersistencePort.existsByStripeEventId(event.eventId())) {
            log.atInfo().addKeyValue("eventId", event.eventId()).log("Webhook event already processed — skipping");
            return;
        }

        // Process by event type
        switch (event.type()) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            case "invoice.paid" -> handleInvoicePaid(event);
            case "invoice.payment_failed" -> handlePaymentFailed(event);
            case "customer.subscription.updated" -> handleSubscriptionUpdated(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            default -> log.atInfo().addKeyValue("type", event.type()).log("Unhandled webhook event type");
        }

        // Log processed event
        WebhookEvent webhookEvent = WebhookEvent.builder()
                .stripeEventId(event.eventId())
                .eventType(event.type())
                .processedAt(Instant.now())
                .payload(payload)
                .build();
        webhookEventPersistencePort.save(webhookEvent);

        log.atInfo().addKeyValue("eventId", event.eventId()).addKeyValue("type", event.type()).log("Webhook event processed");
    }

    private void handleCheckoutCompleted(StripePort.StripeWebhookEvent event) {
        subscriptionPersistencePort.findByStripeCustomerId(event.customerId())
                .ifPresent(sub -> {
                    sub.setStripeSubscriptionId(event.subscriptionId());
                    sub.setStatus(SubscriptionStatus.ACTIVE);
                    sub.setCurrentPeriodStart(Instant.now());

                    // Determine the plan from priceId
                    if (event.priceId() != null) {
                        planPersistencePort.findAllActive().stream()
                                .filter(p -> event.priceId().equals(p.getStripeMonthlyPriceId()))
                                .findFirst()
                                .ifPresent(plan -> {
                                    sub.setPlanId(plan.getId());
                                    sub.setPlanName(plan.getName());
                                    subscriptionService.upgradePlan(sub.getTenantId(), plan.getName());
                                });
                    }

                    subscriptionPersistencePort.save(sub);
                    log.atInfo().addKeyValue("tenantId", sub.getTenantId()).log("Checkout completed — subscription activated");
                });
    }

    private void handleInvoicePaid(StripePort.StripeWebhookEvent event) {
        subscriptionPersistencePort.findByStripeSubscriptionId(event.subscriptionId())
                .ifPresent(sub -> {
                    sub.setStatus(SubscriptionStatus.ACTIVE);
                    subscriptionPersistencePort.save(sub);
                    log.atInfo().addKeyValue("tenantId", sub.getTenantId()).log("Invoice paid — subscription active");
                });
    }

    private void handlePaymentFailed(StripePort.StripeWebhookEvent event) {
        subscriptionPersistencePort.findByStripeSubscriptionId(event.subscriptionId())
                .ifPresent(sub -> {
                    sub.setStatus(SubscriptionStatus.PAST_DUE);
                    subscriptionPersistencePort.save(sub);
                    log.atWarn().addKeyValue("tenantId", sub.getTenantId()).log("Payment failed — subscription PAST_DUE");
                });
    }

    private void handleSubscriptionUpdated(StripePort.StripeWebhookEvent event) {
        log.atInfo().addKeyValue("subscriptionId", event.subscriptionId()).log("Subscription updated in Stripe");
    }

    private void handleSubscriptionDeleted(StripePort.StripeWebhookEvent event) {
        subscriptionPersistencePort.findByStripeSubscriptionId(event.subscriptionId())
                .ifPresent(sub -> {
                    sub.setStatus(SubscriptionStatus.CANCELLED);
                    subscriptionPersistencePort.save(sub);
                    log.atWarn().addKeyValue("tenantId", sub.getTenantId()).log("Subscription deleted — CANCELLED");
                });
    }
}
