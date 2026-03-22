package com.rivoo.billing.domain.port.in;

import com.rivoo.billing.application.dto.SubscriptionResponse;

public interface GetSubscriptionUseCase {

    SubscriptionResponse getByTenantId(String tenantId);
}
