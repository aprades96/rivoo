package com.rivoo.appointment.domain.port.out;

import com.rivoo.appointment.application.dto.EmployeeWorkingHoursDto;

import java.math.BigDecimal;
import java.util.List;

public interface StaffServicePort {

    StaffEmployeeInfo getEmployee(String tenantId, String employeeExternalId);

    StaffServiceInfo getService(String tenantId, String serviceExternalId);

    List<EmployeeWorkingHoursDto> getEmployeeWorkingHours(String tenantId, String employeeExternalId);

    record StaffEmployeeInfo(String externalId, String firstName, String lastName, boolean active) {
        public String fullName() {
            return firstName + " " + lastName;
        }
    }

    record StaffServiceInfo(String externalId, String name, BigDecimal price, int durationMinutes, boolean active) {}
}
