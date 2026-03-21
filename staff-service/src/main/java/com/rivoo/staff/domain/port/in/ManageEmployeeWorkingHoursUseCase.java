package com.rivoo.staff.domain.port.in;

import com.rivoo.staff.application.dto.WorkingHoursRequest;
import com.rivoo.staff.application.dto.WorkingHoursResponse;

import java.util.List;

public interface ManageEmployeeWorkingHoursUseCase {

    List<WorkingHoursResponse> getWorkingHours(String employeeExternalId);

    List<WorkingHoursResponse> getWorkingHoursInternal(String tenantId, String employeeExternalId);

    List<WorkingHoursResponse> updateWorkingHours(String tenantId, String employeeExternalId, List<WorkingHoursRequest> request);
}
