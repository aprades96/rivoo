package com.rivoo.billing.infrastructure.mapper;

import com.rivoo.billing.domain.model.SubscriptionPlan;
import com.rivoo.billing.infrastructure.adapter.out.persistence.entity.SubscriptionPlanJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PlanPersistenceMapper {

    SubscriptionPlanJpaEntity toJpaEntity(SubscriptionPlan plan);

    SubscriptionPlan toDomain(SubscriptionPlanJpaEntity entity);
}
