package com.rivoo.client.domain.port.in;

import com.rivoo.client.application.dto.ClientResponse;
import com.rivoo.client.application.dto.UpdateClientRequest;

public interface UpdateClientUseCase {

    ClientResponse update(String externalId, UpdateClientRequest request);
}
