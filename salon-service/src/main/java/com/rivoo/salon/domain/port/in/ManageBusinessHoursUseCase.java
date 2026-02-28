package com.rivoo.salon.domain.port.in;

import com.rivoo.salon.application.dto.BusinessHoursRequest;
import com.rivoo.salon.application.dto.BusinessHoursResponse;

import java.util.List;

public interface ManageBusinessHoursUseCase {

    List<BusinessHoursResponse> getBusinessHours(String tenantId);

    List<BusinessHoursResponse> updateBusinessHours(String tenantId, List<BusinessHoursRequest> request);
}
