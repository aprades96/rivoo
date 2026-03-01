package com.rivoo.staff.domain.port.in;

import com.rivoo.staff.application.dto.CreateEmployeeRequest;
import com.rivoo.staff.application.dto.EmployeeResponse;

public interface CreateEmployeeUseCase {

    EmployeeResponse create(String tenantId, CreateEmployeeRequest request);
}
