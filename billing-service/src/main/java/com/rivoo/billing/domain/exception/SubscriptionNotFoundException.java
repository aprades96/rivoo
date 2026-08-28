package com.rivoo.billing.domain.exception;

import com.rivoo.common.exception.ResourceNotFoundException;

public class SubscriptionNotFoundException extends ResourceNotFoundException {

    public SubscriptionNotFoundException(String identifier) {
        super("subscription", identifier);
    }

    /**
     * Not reachable anonymously. Throw sites: BillingPortalService and CheckoutService
     * (hasRole('SALON_OWNER')), PlanLimitsService (/api/internal/**, PSK-gated) and
     * SubscriptionService#findSubscriptionOrThrow. The one caller of the latter on the anonymous
     * POST /api/webhooks/stripe is WebhookService#handleCheckoutCompleted, which only calls
     * upgradePlan inside an ifPresent on the very subscription findSubscriptionOrThrow then looks
     * up, so the throw is unreachable there. If that guard ever changes, re-evaluate this override.
     */
    @Override
    public String clientSafeDetail() {
        return getMessage();
    }
}
