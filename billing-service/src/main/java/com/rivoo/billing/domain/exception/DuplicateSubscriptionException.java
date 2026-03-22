package com.rivoo.billing.domain.exception;

import com.rivoo.common.exception.BusinessValidationException;

public class DuplicateSubscriptionException extends BusinessValidationException {

    public DuplicateSubscriptionException(String tenantId) {
        super("Tenant '" + tenantId + "' already has an active subscription");
    }
}
