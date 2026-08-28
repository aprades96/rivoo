package com.rivoo.billing.application.dto;

import java.math.BigDecimal;

/**
 * One entry of the ANONYMOUS plan catalogue ({@code GET /api/v1/billing/plans}, permitAll
 * in both {@code BillingSecurityConfig} and the gateway's {@code GatewaySecurityConfig}).
 * Everything here is public by construction — pricing-page material only.
 */
public record PlanResponse(
        String id,
        String name,
        String displayName,
        BigDecimal monthlyPrice,
        int trialDays,
        // What the tier includes, so a comparison screen can render "hasta 3 empleados" /
        // "recordatorios por SMS" without a second call. Additive: rivoo-frontend's
        // PlanInfo (src/types/billing.ts) does not declare it, and unknown JSON keys are
        // ignored there, so shipping this does not require a frontend change.
        PlanLimitsPublicResponse limits
) {
}
