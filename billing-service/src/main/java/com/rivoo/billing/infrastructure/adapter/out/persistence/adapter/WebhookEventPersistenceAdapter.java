package com.rivoo.billing.infrastructure.adapter.out.persistence.adapter;

import com.rivoo.billing.domain.model.WebhookEvent;
import com.rivoo.billing.domain.port.out.WebhookEventPersistencePort;
import com.rivoo.billing.infrastructure.adapter.out.persistence.entity.WebhookEventJpaEntity;
import com.rivoo.billing.infrastructure.adapter.out.persistence.repository.WebhookEventJpaRepository;
import com.rivoo.billing.infrastructure.mapper.WebhookEventPersistenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebhookEventPersistenceAdapter implements WebhookEventPersistencePort {

    private final WebhookEventJpaRepository repository;
    private final WebhookEventPersistenceMapper mapper;

    @Override
    public WebhookEvent save(WebhookEvent webhookEvent) {
        WebhookEventJpaEntity entity = mapper.toJpaEntity(webhookEvent);
        WebhookEventJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public boolean existsByStripeEventId(String stripeEventId) {
        return repository.existsByStripeEventId(stripeEventId);
    }
}
