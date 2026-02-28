package com.rivoo.salon.infrastructure.mapper;

import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonBusinessHours;
import com.rivoo.salon.infrastructure.adapter.out.persistence.entity.SalonBusinessHoursJpaEntity;
import com.rivoo.salon.infrastructure.adapter.out.persistence.entity.SalonJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SalonPersistenceMapper {

    SalonJpaEntity toJpaEntity(Salon salon);

    Salon toDomain(SalonJpaEntity entity);

    SalonBusinessHoursJpaEntity toJpaEntity(SalonBusinessHours hours);

    SalonBusinessHours toDomain(SalonBusinessHoursJpaEntity entity);
}
