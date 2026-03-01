package com.rivoo.client.domain.port.in;

import com.rivoo.client.application.dto.ClientResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GetClientUseCase {

    ClientResponse getByExternalId(String externalId);

    Page<ClientResponse> list(Pageable pageable);
}
