package com.rivoo.salon.domain.port.in;

import com.rivoo.salon.domain.model.SalonStatus;

public interface ManageSalonStatusUseCase {

    void updateStatus(String tenantId, SalonStatus status);
}
