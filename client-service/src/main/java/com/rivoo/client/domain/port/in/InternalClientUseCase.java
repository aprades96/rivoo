package com.rivoo.client.domain.port.in;

import com.rivoo.client.application.dto.ClientInternalResponse;
import com.rivoo.client.application.dto.FindOrCreateClientRequest;

import java.time.Instant;

public interface InternalClientUseCase {

    ClientInternalResponse getByExternalIdAndTenant(String externalId, String tenantId);

    ClientInternalResponse findOrCreate(String tenantId, FindOrCreateClientRequest request);

    void registerVisit(String tenantId, String clientExternalId, Instant visitAt);
}
