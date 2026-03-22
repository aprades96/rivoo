package com.rivoo.billing.infrastructure.adapter.out.persistence.entity;

import com.rivoo.billing.domain.model.SubscriptionStatus;
import com.rivoo.common.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "subscriptions")
public class SubscriptionJpaEntity extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true, length = 44)
    private String externalId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubscriptionStatus status;

    @Column(name = "stripe_customer_id", unique = true, length = 100)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", unique = true, length = 100)
    private String stripeSubscriptionId;

    @Column(name = "trial_start")
    private Instant trialStart;

    @Column(name = "trial_end")
    private Instant trialEnd;

    @Column(name = "current_period_start")
    private Instant currentPeriodStart;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "cancel_at_period_end")
    private boolean cancelAtPeriodEnd;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
