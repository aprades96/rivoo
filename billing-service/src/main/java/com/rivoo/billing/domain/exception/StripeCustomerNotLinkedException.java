package com.rivoo.billing.domain.exception;

import com.rivoo.common.exception.BusinessValidationException;

/**
 * The tenant has a subscription but it is not linked to a Stripe Customer yet,
 * so no Stripe-side operation (billing portal, checkout) can be performed on it.
 *
 * <p>Extends {@link BusinessValidationException} so the shared
 * {@code GlobalExceptionHandler} maps it to 422 Unprocessable Entity: the request
 * is well-formed, it is the resource state that makes it unprocessable.
 */
public class StripeCustomerNotLinkedException extends BusinessValidationException {

    public StripeCustomerNotLinkedException(String tenantId) {
        super("Tenant '" + tenantId + "' has no Stripe customer linked to its subscription");
    }

    /**
     * Authenticated-only: the single throw site is BillingPortalService, reached from
     * POST /api/v1/billing/portal, hasRole('SALON_OWNER'). The tenant in the message is the
     * caller's own, and the message is the one actionable hint the owner gets.
     */
    @Override
    public String clientSafeDetail() {
        return getMessage();
    }
}
