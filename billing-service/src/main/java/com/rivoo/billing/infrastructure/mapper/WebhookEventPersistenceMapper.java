package com.rivoo.billing.infrastructure.mapper;

import com.rivoo.billing.domain.model.WebhookEvent;
import com.rivoo.billing.infrastructure.adapter.out.persistence.entity.WebhookEventJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WebhookEventPersistenceMapper {

    WebhookEventJpaEntity toJpaEntity(WebhookEvent webhookEvent);

    WebhookEvent toDomain(WebhookEventJpaEntity entity);
}
