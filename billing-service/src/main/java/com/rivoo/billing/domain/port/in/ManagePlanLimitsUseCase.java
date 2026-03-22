package com.rivoo.billing.domain.port.in;

import com.rivoo.billing.application.dto.PlanLimitsResponse;

public interface ManagePlanLimitsUseCase {

    PlanLimitsResponse getPlanLimits(String tenantId, boolean forWriteOperation);
}
