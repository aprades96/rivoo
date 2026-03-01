package com.rivoo.staff.domain.port.in;

import com.rivoo.staff.application.dto.EmployeeInternalResponse;
import com.rivoo.staff.application.dto.EmployeeResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GetEmployeeUseCase {

    EmployeeResponse getByExternalId(String externalId);

    Page<EmployeeResponse> list(Pageable pageable);

    EmployeeInternalResponse getInternal(String tenantId, String employeeExternalId);
}
