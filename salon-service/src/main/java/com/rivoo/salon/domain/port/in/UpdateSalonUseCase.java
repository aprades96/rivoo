package com.rivoo.salon.domain.port.in;

import com.rivoo.salon.application.dto.SalonResponse;
import com.rivoo.salon.application.dto.UpdateSalonRequest;

public interface UpdateSalonUseCase {

    SalonResponse update(String tenantId, UpdateSalonRequest request);
}
