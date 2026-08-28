package com.rivoo.billing.domain.exception;

import com.rivoo.common.exception.BusinessValidationException;

public class DuplicateSubscriptionException extends BusinessValidationException {

    public DuplicateSubscriptionException(String tenantId) {
        super("Tenant '" + tenantId + "' already has an active subscription");
    }

    /**
     * Not reachable anonymously: the single throw site is SubscriptionService#create, exposed
     * only through POST /api/internal/billing/subscriptions, which InternalEndpointFilter gates
     * with the PSK. The tenant named in the message is the one the caller just sent.
     */
    @Override
    public String clientSafeDetail() {
        return getMessage();
    }
}
