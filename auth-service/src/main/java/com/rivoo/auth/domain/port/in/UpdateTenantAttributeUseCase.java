package com.rivoo.auth.domain.port.in;

import com.rivoo.auth.application.dto.UpdateAttributeRequest;

public interface UpdateTenantAttributeUseCase {
    void updateTenantAttributes(String tenantId, UpdateAttributeRequest request);
}
