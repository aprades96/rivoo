package com.rivoo.billing.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

    private Long id;
    private String externalId;
    private String tenantId;
    private Long planId;
    private SubscriptionStatus status;
    private String stripeCustomerId;
    private String stripeSubscriptionId;
    private Instant trialStart;
    private Instant trialEnd;
    private Instant currentPeriodStart;
    private Instant currentPeriodEnd;
    private boolean cancelAtPeriodEnd;
    private Instant createdAt;
    private Instant updatedAt;

    // Transient — populated when loaded with plan info
    private PlanName planName;
}
