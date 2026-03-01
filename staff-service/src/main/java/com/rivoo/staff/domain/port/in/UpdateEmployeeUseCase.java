package com.rivoo.staff.domain.port.in;

import com.rivoo.staff.application.dto.EmployeeResponse;
import com.rivoo.staff.application.dto.UpdateEmployeeRequest;

public interface UpdateEmployeeUseCase {

    EmployeeResponse update(String tenantId, String externalId, UpdateEmployeeRequest request);
}
