package com.rivoo.appointment.infrastructure.mapper;

import com.rivoo.appointment.domain.model.Appointment;
import com.rivoo.appointment.infrastructure.adapter.out.persistence.entity.AppointmentJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AppointmentPersistenceMapper {

    AppointmentJpaEntity toJpaEntity(Appointment appointment);

    Appointment toDomain(AppointmentJpaEntity entity);
}
