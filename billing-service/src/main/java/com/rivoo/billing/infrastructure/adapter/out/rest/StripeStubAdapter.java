package com.rivoo.billing.infrastructure.adapter.out.rest;

import com.rivoo.billing.domain.port.out.StripePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stub implementation of StripePort for local development and testing.
 * Replace with real Stripe SDK integration before production.
 */
@Component
public class StripeStubAdapter implements StripePort {

    private static final Logger log = LoggerFactory.getLogger(StripeStubAdapter.class);

    private static final Pattern FIELD_PATTERN = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"([^\"]+)\"");

    @Override
    public String createCustomer(String tenantId, String email, String salonName) {
        String customerId = "cus_mock_" + UUID.randomUUID().toString().replace("-", "");
        log.atInfo()
                .addKeyValue("email", email)
                .addKeyValue("salonName", salonName)
                .addKeyValue("stripeCustomerId", customerId)
                .log("Stripe stub: created mock customer");
        return customerId;
    }

    @Override
    public String createCheckoutSession(String stripeCustomerId, String stripePriceId,
                                        String successUrl, String cancelUrl) {
        String sessionUrl = "https://checkout.stripe.com/mock-session/" + UUID.randomUUID();
        log.atInfo()
                .addKeyValue("stripeCustomerId", stripeCustomerId)
                .addKeyValue("stripePriceId", stripePriceId)
                .addKeyValue("checkoutUrl", sessionUrl)
                .log("Stripe stub: created mock checkout session");
        return sessionUrl;
    }

    @Override
    public String createBillingPortalSession(String stripeCustomerId, String returnUrl) {
        String portalUrl = "https://billing.stripe.com/mock-portal/" + UUID.randomUUID();
        log.atInfo()
                .addKeyValue("stripeCustomerId", stripeCustomerId)
                .addKeyValue("returnUrl", returnUrl)
                .addKeyValue("portalUrl", portalUrl)
                .log("Stripe stub: created mock billing portal session");
        return portalUrl;
    }

    @Override
    public StripeWebhookEvent constructEvent(String payload, String signatureHeader) {
        // Stub: parse known fields from a simple JSON payload.
        // Expected payload format:
        // {"eventId":"evt_xxx","type":"checkout.session.completed","customerId":"cus_xxx",
        //  "subscriptionId":"sub_xxx","priceId":"price_xxx"}
        try {
            String eventId = extractField(payload, "eventId");
            String type = extractField(payload, "type");
            String customerId = extractField(payload, "customerId");
            String subscriptionId = extractField(payload, "subscriptionId");
            String priceId = extractField(payload, "priceId");

            if (eventId == null || type == null) {
                log.atWarn()
                        .addKeyValue("payload", payload)
                        .log("Stripe stub: invalid webhook payload — missing required fields");
                return null;
            }

            log.atInfo()
                    .addKeyValue("eventId", eventId)
                    .addKeyValue("eventType", type)
                    .log("Stripe stub: constructed mock webhook event");

            return new StripeWebhookEvent(eventId, type, customerId, subscriptionId, priceId);
        } catch (Exception ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("payload", payload)
                    .log("Stripe stub: failed to parse webhook payload");
            return null;
        }
    }

    private String extractField(String json, String fieldName) {
        Matcher matcher = FIELD_PATTERN.matcher(json);
        while (matcher.find()) {
            if (matcher.group(1).equals(fieldName)) {
                return matcher.group(2);
            }
        }
        return null;
    }
}
