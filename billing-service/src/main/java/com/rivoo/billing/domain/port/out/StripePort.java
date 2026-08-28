package com.rivoo.billing.domain.port.out;

public interface StripePort {

    String createCustomer(String tenantId, String email, String salonName);

    String createCheckoutSession(String stripeCustomerId, String stripePriceId, String successUrl, String cancelUrl);

    /**
     * Creates a Stripe Billing Portal session for an existing customer and returns its URL.
     * The customer is sent back to returnUrl when they leave the portal.
     */
    String createBillingPortalSession(String stripeCustomerId, String returnUrl);

    /**
     * Constructs a Stripe webhook event from the raw payload. Returns null if the payload
     * cannot be parsed into an event.
     *
     * <p><strong>No signature is verified today.</strong> The only implementation
     * ({@code StripeStubAdapter}) never reads {@code signatureHeader}, and the controller
     * declares the {@code Stripe-Signature} header as {@code required = false}. So
     * {@code POST /api/webhooks/stripe} is an anonymous endpoint that accepts forged events
     * and mutates subscription state — a caller who knows a {@code stripeSubscriptionId} can
     * flip that subscription to ACTIVE or CANCELLED. Verifying the signature is a prerequisite
     * for replacing the stub with the real Stripe SDK; until then, treat this parameter as
     * accepted and ignored, not as a security control.
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
