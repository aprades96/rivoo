package com.rivoo.billing.application.dto;

import java.math.BigDecimal;

public record PlanResponse(
        String id,
        String name,
        String displayName,
        BigDecimal monthlyPrice,
        int trialDays
) {
}
