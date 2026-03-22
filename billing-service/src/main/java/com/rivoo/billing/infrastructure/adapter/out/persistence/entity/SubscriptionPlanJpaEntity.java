package com.rivoo.billing.infrastructure.adapter.out.persistence.entity;

import com.rivoo.billing.domain.model.PlanName;
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

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlanJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true, length = 44)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true)
    private PlanName name;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "monthly_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyPrice;

    @Column(name = "stripe_monthly_price_id", length = 100)
    private String stripeMonthlyPriceId;

    @Column(name = "trial_days")
    private int trialDays;

    @Column(name = "active")
    private boolean active;

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
