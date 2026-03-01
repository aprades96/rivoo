package com.rivoo.staff.infrastructure.mapper;

import com.rivoo.staff.domain.model.Employee;
import com.rivoo.staff.domain.model.EmployeeWorkingHours;
import com.rivoo.staff.infrastructure.adapter.out.persistence.entity.EmployeeJpaEntity;
import com.rivoo.staff.infrastructure.adapter.out.persistence.entity.EmployeeWorkingHoursJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EmployeePersistenceMapper {

    EmployeeJpaEntity toJpaEntity(Employee employee);

    Employee toDomain(EmployeeJpaEntity entity);

    EmployeeWorkingHoursJpaEntity toJpaEntity(EmployeeWorkingHours hours);

    EmployeeWorkingHours toDomain(EmployeeWorkingHoursJpaEntity entity);
}
