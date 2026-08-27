package com.rivoo.billing.domain.port.in;

import com.rivoo.billing.application.dto.PortalResponse;

public interface BillingPortalUseCase {

    PortalResponse createPortalSession(String tenantId);
}
