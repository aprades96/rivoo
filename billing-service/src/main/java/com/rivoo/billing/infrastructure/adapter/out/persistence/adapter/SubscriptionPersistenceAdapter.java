package com.rivoo.billing.infrastructure.adapter.out.persistence.adapter;

import com.rivoo.billing.domain.model.Subscription;
import com.rivoo.billing.domain.port.out.SubscriptionPersistencePort;
import com.rivoo.billing.infrastructure.adapter.out.persistence.entity.SubscriptionJpaEntity;
import com.rivoo.billing.infrastructure.adapter.out.persistence.repository.SubscriptionJpaRepository;
import com.rivoo.billing.infrastructure.mapper.SubscriptionPersistenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SubscriptionPersistenceAdapter implements SubscriptionPersistencePort {

    private final SubscriptionJpaRepository repository;
    private final SubscriptionPersistenceMapper mapper;

    @Override
    public Subscription save(Subscription subscription) {
        SubscriptionJpaEntity entity = mapper.toJpaEntity(subscription);
        SubscriptionJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Subscription> findByTenantId(String tenantId) {
        return repository.findByTenantId(tenantId).map(mapper::toDomain);
    }

    @Override
    public Optional<Subscription> findByStripeCustomerId(String stripeCustomerId) {
        return repository.findByStripeCustomerId(stripeCustomerId).map(mapper::toDomain);
    }

    @Override
    public Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId) {
        return repository.findByStripeSubscriptionId(stripeSubscriptionId).map(mapper::toDomain);
    }

    @Override
    public boolean existsByTenantId(String tenantId) {
        return repository.existsByTenantId(tenantId);
    }
}
