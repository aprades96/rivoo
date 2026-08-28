package com.rivoo.billing.domain.exception;

import com.rivoo.common.exception.ResourceNotFoundException;

public class PlanNotFoundException extends ResourceNotFoundException {

    public PlanNotFoundException(String identifier) {
        super("plan", identifier);
    }

    /**
     * Not reachable anonymously. Throw sites: CheckoutService (POST /api/v1/billing/checkout-session,
     * hasRole('SALON_OWNER')), SubscriptionService#create / #updateStatus (both /api/internal/**,
     * PSK-gated) and SubscriptionService#getByTenantId (GET /api/v1/billing/subscription,
     * hasRole('SALON_OWNER')). The one remaining caller, WebhookService via #upgradePlan on the
     * anonymous POST /api/webhooks/stripe, cannot reach the throw: it only calls upgradePlan with
     * a PlanName it just read back from planPersistencePort.findAllActive(), so findByName always
     * resolves. If that guard ever changes, this override must be re-evaluated.
     */
    @Override
    public String clientSafeDetail() {
        return getMessage();
    }
}
