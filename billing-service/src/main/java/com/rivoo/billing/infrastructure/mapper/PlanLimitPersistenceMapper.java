package com.rivoo.billing.infrastructure.mapper;

import com.rivoo.billing.domain.model.PlanLimit;
import com.rivoo.billing.infrastructure.adapter.out.persistence.entity.PlanLimitJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PlanLimitPersistenceMapper {

    PlanLimitJpaEntity toJpaEntity(PlanLimit planLimit);

    PlanLimit toDomain(PlanLimitJpaEntity entity);
}
