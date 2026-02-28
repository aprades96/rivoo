package com.rivoo.auth.infrastructure.adapter.out.persistence.adapter;

import com.rivoo.auth.domain.model.OnboardingEvent;
import com.rivoo.auth.domain.port.out.OnboardingEventPort;
import com.rivoo.auth.infrastructure.adapter.out.persistence.entity.OnboardingEventJpaEntity;
import com.rivoo.auth.infrastructure.adapter.out.persistence.repository.OnboardingEventJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class OnboardingEventPersistenceAdapter implements OnboardingEventPort {

    private final OnboardingEventJpaRepository repository;

    public OnboardingEventPersistenceAdapter(OnboardingEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public OnboardingEvent save(OnboardingEvent event) {
        OnboardingEventJpaEntity entity = toJpaEntity(event);
        OnboardingEventJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    private OnboardingEventJpaEntity toJpaEntity(OnboardingEvent event) {
        OnboardingEventJpaEntity entity = new OnboardingEventJpaEntity();
        entity.setTenantId(event.getTenantId());
        entity.setKeycloakUserId(event.getKeycloakUserId());
        entity.setEmail(event.getEmail());
        entity.setEventType(event.getEventType());
        entity.setDetails(event.getDetails());
        return entity;
    }

    private OnboardingEvent toDomain(OnboardingEventJpaEntity entity) {
        OnboardingEvent event = new OnboardingEvent();
        event.setId(entity.getId());
        event.setTenantId(entity.getTenantId());
        event.setKeycloakUserId(entity.getKeycloakUserId());
        event.setEmail(entity.getEmail());
        event.setEventType(entity.getEventType());
        event.setDetails(entity.getDetails());
        event.setCreatedAt(entity.getCreatedAt());
        return event;
    }
}
