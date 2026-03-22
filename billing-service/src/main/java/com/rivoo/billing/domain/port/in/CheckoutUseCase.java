package com.rivoo.billing.domain.port.in;

import com.rivoo.billing.application.dto.CheckoutRequest;
import com.rivoo.billing.application.dto.CheckoutResponse;

public interface CheckoutUseCase {

    CheckoutResponse createCheckoutSession(String tenantId, CheckoutRequest request);
}
