package com.rivoo.staff.domain.port.out;

import com.rivoo.staff.domain.model.EmployeeWorkingHours;

import java.util.List;

public interface WorkingHoursPersistencePort {

    List<EmployeeWorkingHours> findByEmployeeId(Long employeeId);

    List<EmployeeWorkingHours> saveAll(List<EmployeeWorkingHours> hours);

    void deleteByEmployeeId(Long employeeId);
}
