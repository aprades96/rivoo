package com.rivoo.billing.domain.port.out;

import com.rivoo.billing.domain.model.Subscription;

import java.util.Optional;

public interface SubscriptionPersistencePort {

    Subscription save(Subscription subscription);

    Optional<Subscription> findByTenantId(String tenantId);

    Optional<Subscription> findByStripeCustomerId(String stripeCustomerId);

    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    boolean existsByTenantId(String tenantId);
}
