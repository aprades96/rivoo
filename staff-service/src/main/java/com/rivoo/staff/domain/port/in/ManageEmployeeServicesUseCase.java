package com.rivoo.staff.domain.port.in;

import com.rivoo.staff.application.dto.AssignServicesRequest;
import com.rivoo.staff.application.dto.EmployeeServiceResponse;

import java.util.List;

public interface ManageEmployeeServicesUseCase {

    List<EmployeeServiceResponse> assignServices(String tenantId, String employeeExternalId, AssignServicesRequest request);

    List<EmployeeServiceResponse> getAssignedServices(String employeeExternalId);
}
