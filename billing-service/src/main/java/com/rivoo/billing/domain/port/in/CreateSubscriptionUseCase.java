package com.rivoo.billing.domain.port.in;

import com.rivoo.billing.application.dto.CreateSubscriptionRequest;
import com.rivoo.billing.application.dto.SubscriptionResponse;

public interface CreateSubscriptionUseCase {

    SubscriptionResponse create(CreateSubscriptionRequest request);
}
