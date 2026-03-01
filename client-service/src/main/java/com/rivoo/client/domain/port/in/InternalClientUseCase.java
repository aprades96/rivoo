package com.rivoo.client.domain.port.in;

import com.rivoo.client.application.dto.ClientInternalResponse;
import com.rivoo.client.application.dto.FindOrCreateClientRequest;

public interface InternalClientUseCase {

    ClientInternalResponse getByExternalIdAndTenant(String externalId, String tenantId);

    ClientInternalResponse findOrCreate(String tenantId, FindOrCreateClientRequest request);
}
