package com.rivoo.staff.infrastructure.mapper;

import com.rivoo.staff.application.dto.EmployeeInternalResponse;
import com.rivoo.staff.application.dto.EmployeePublicResponse;
import com.rivoo.staff.application.dto.EmployeeResponse;
import com.rivoo.staff.application.dto.EmployeeServiceResponse;
import com.rivoo.staff.application.dto.WorkingHoursResponse;
import com.rivoo.staff.domain.model.Employee;
import com.rivoo.staff.domain.model.EmployeeServiceAssignment;
import com.rivoo.staff.domain.model.EmployeeWorkingHours;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EmployeeDtoMapper {

    @Mapping(target = "id", source = "externalId")
    @Mapping(target = "role", expression = "java(employee.getRole().name())")
    EmployeeResponse toResponse(Employee employee);

    @Mapping(target = "id", source = "employee.externalId")
    @Mapping(target = "serviceIds", source = "serviceIds")
    EmployeePublicResponse toPublicResponse(Employee employee, List<String> serviceIds);

    @Mapping(target = "id", source = "externalId")
    @Mapping(target = "role", expression = "java(employee.getRole().name())")
    EmployeeInternalResponse toInternalResponse(Employee employee);

    @Mapping(target = "isOpen", source = "open")
    WorkingHoursResponse toWorkingHoursResponse(EmployeeWorkingHours hours);

    @Mapping(target = "serviceId", source = "serviceExternalId")
    @Mapping(target = "effectiveDuration", expression = "java(assignment.getEffectiveDuration())")
    @Mapping(target = "effectivePrice", expression = "java(assignment.getEffectivePrice())")
    EmployeeServiceResponse toEmployeeServiceResponse(EmployeeServiceAssignment assignment);
}
