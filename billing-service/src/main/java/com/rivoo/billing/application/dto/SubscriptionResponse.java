package com.rivoo.billing.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SubscriptionResponse(
        String id,
        String tenantId,
        String planName,
        String planDisplayName,
        BigDecimal monthlyPrice,
        String status,
        String stripeCustomerId,
        String stripeSubscriptionId,
        Instant trialStart,
        Instant trialEnd,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        Instant createdAt
) {
}
