package com.rivoo.salon.infrastructure.mapper;

import com.rivoo.salon.application.dto.BusinessHoursResponse;
import com.rivoo.salon.application.dto.EmployeePublicResponseDto;
import com.rivoo.salon.application.dto.SalonPublicResponse;
import com.rivoo.salon.application.dto.SalonResponse;
import com.rivoo.salon.application.dto.ServicePublicResponseDto;
import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonBusinessHours;
import com.rivoo.salon.domain.port.out.StaffServicePort;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SalonDtoMapper {

    @Mapping(target = "id", source = "externalId")
    @Mapping(target = "subscriptionPlan", expression = "java(salon.getSubscriptionPlan().name())")
    @Mapping(target = "status", expression = "java(salon.getStatus().name())")
    SalonResponse toResponse(Salon salon);

    SalonPublicResponse toPublicResponse(Salon salon, List<BusinessHoursResponse> businessHours,
                                          List<ServicePublicResponseDto> services,
                                          List<EmployeePublicResponseDto> employees);

    @Mapping(target = "isOpen", source = "open")
    BusinessHoursResponse toBusinessHoursResponse(SalonBusinessHours hours);

    ServicePublicResponseDto toServicePublicDto(StaffServicePort.ServicePublicInfo info);

    EmployeePublicResponseDto toEmployeePublicDto(StaffServicePort.EmployeePublicInfo info);
}
