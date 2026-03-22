package com.rivoo.billing.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlan {

    private Long id;
    private String externalId;
    private PlanName name;
    private String displayName;
    private BigDecimal monthlyPrice;
    private String stripeMonthlyPriceId;
    private int trialDays;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
