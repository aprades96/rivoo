package com.rivoo.billing.domain.port.in;

import com.rivoo.billing.application.dto.SubscriptionResponse;

public interface UpdateSubscriptionStatusUseCase {

    SubscriptionResponse updateStatus(String tenantId, String newStatus);
}
