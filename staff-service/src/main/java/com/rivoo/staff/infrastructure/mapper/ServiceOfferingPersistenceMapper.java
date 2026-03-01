package com.rivoo.staff.infrastructure.mapper;

import com.rivoo.staff.domain.model.ServiceOffering;
import com.rivoo.staff.infrastructure.adapter.out.persistence.entity.ServiceOfferingJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ServiceOfferingPersistenceMapper {

    ServiceOfferingJpaEntity toJpaEntity(ServiceOffering service);

    ServiceOffering toDomain(ServiceOfferingJpaEntity entity);
}
