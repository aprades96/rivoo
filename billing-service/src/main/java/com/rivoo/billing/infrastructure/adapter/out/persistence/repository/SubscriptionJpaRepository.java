package com.rivoo.billing.infrastructure.adapter.out.persistence.repository;

import com.rivoo.billing.infrastructure.adapter.out.persistence.entity.SubscriptionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionJpaEntity, Long> {

    Optional<SubscriptionJpaEntity> findByTenantId(String tenantId);

    Optional<SubscriptionJpaEntity> findByStripeCustomerId(String stripeCustomerId);

    Optional<SubscriptionJpaEntity> findByStripeSubscriptionId(String stripeSubscriptionId);

    boolean existsByTenantId(String tenantId);
}
