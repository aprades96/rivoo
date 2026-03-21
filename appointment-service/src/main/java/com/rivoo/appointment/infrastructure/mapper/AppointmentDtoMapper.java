package com.rivoo.appointment.infrastructure.mapper;

import com.rivoo.appointment.application.dto.AppointmentInternalResponse;
import com.rivoo.appointment.application.dto.AppointmentResponse;
import com.rivoo.appointment.domain.model.Appointment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AppointmentDtoMapper {

    @Mapping(target = "id", source = "externalId")
    @Mapping(target = "status", expression = "java(appointment.getStatus() != null ? appointment.getStatus().name() : null)")
    @Mapping(target = "cancelledBy", expression = "java(appointment.getCancelledBy() != null ? appointment.getCancelledBy().name() : null)")
    @Mapping(target = "source", expression = "java(appointment.getSource() != null ? appointment.getSource().name() : null)")
    AppointmentResponse toResponse(Appointment appointment);

    @Mapping(target = "id", source = "externalId")
    @Mapping(target = "status", expression = "java(appointment.getStatus() != null ? appointment.getStatus().name() : null)")
    @Mapping(target = "source", expression = "java(appointment.getSource() != null ? appointment.getSource().name() : null)")
    AppointmentInternalResponse toInternalResponse(Appointment appointment);
}
