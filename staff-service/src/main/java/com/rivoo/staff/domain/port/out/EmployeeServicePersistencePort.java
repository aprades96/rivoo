package com.rivoo.staff.domain.port.out;

import com.rivoo.staff.domain.model.EmployeeServiceAssignment;

import java.util.List;

public interface EmployeeServicePersistencePort {

    List<EmployeeServiceAssignment> findByEmployeeId(Long employeeId);

    void deleteByEmployeeId(Long employeeId);

    List<EmployeeServiceAssignment> saveAll(List<EmployeeServiceAssignment> assignments);
}
