package com.rivoo.client.domain.port.in;

import com.rivoo.client.application.dto.ClientResponse;
import com.rivoo.client.application.dto.CreateClientRequest;

public interface CreateClientUseCase {

    ClientResponse create(String tenantId, CreateClientRequest request);
}
