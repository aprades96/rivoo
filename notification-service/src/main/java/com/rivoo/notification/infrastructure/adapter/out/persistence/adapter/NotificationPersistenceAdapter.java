package com.rivoo.notification.infrastructure.adapter.out.persistence.adapter;

import com.rivoo.notification.domain.model.Notification;
import com.rivoo.notification.domain.port.out.NotificationPersistencePort;
import com.rivoo.notification.infrastructure.adapter.out.persistence.entity.NotificationLogJpaEntity;
import com.rivoo.notification.infrastructure.adapter.out.persistence.repository.NotificationLogJpaRepository;
import com.rivoo.notification.infrastructure.mapper.NotificationPersistenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class NotificationPersistenceAdapter implements NotificationPersistencePort {

    private final NotificationLogJpaRepository repository;
    private final NotificationPersistenceMapper mapper;

    @Override
    public Notification save(Notification notification) {
        NotificationLogJpaEntity entity = mapper.toJpaEntity(notification);
        NotificationLogJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public List<Notification> findPendingReadyToSend(Instant now) {
        return repository.findPendingReadyToSend(now)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public void cancelByReferenceId(String referenceId, String referenceType) {
        repository.cancelByReferenceId(referenceId, referenceType);
    }

    @Override
    public Optional<Notification> findByExternalId(String externalId) {
        return repository.findByExternalId(externalId).map(mapper::toDomain);
    }
}
