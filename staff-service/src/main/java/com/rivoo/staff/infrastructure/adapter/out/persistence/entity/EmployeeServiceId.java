package com.rivoo.staff.infrastructure.adapter.out.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

public class EmployeeServiceId implements Serializable {

    private Long employeeId;
    private Long serviceId;

    public EmployeeServiceId() {
    }

    public EmployeeServiceId(Long employeeId, Long serviceId) {
        this.employeeId = employeeId;
        this.serviceId = serviceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmployeeServiceId that = (EmployeeServiceId) o;
        return Objects.equals(employeeId, that.employeeId) && Objects.equals(serviceId, that.serviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(employeeId, serviceId);
    }
}
