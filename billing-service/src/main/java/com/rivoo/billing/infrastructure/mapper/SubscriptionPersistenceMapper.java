package com.rivoo.billing.infrastructure.mapper;

import com.rivoo.billing.domain.model.Subscription;
import com.rivoo.billing.infrastructure.adapter.out.persistence.entity.SubscriptionJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubscriptionPersistenceMapper {

    @Mapping(target = "tenantId", source = "tenantId")
    SubscriptionJpaEntity toJpaEntity(Subscription subscription);

    @Mapping(target = "tenantId", source = "tenantId")
    @Mapping(target = "planName", ignore = true)
    Subscription toDomain(SubscriptionJpaEntity entity);
}
