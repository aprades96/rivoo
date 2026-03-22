package com.rivoo.billing.domain.port.out;

public interface StripePort {

    String createCustomer(String tenantId, String email, String salonName);

    String createCheckoutSession(String stripeCustomerId, String stripePriceId, String successUrl, String cancelUrl);

    /**
     * Constructs and validates a Stripe webhook event from the raw payload and signature header.
     * Returns null if the signature is invalid.
     */
    StripeWebhookEvent constructEvent(String payload, String signatureHeader);

    record StripeWebhookEvent(
            String eventId,
            String type,
            String customerId,
            String subscriptionId,
            String priceId
    ) {}
}
