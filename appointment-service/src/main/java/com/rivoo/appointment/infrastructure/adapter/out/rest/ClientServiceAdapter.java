package com.rivoo.appointment.infrastructure.adapter.out.rest;

import com.rivoo.appointment.domain.port.out.ClientServicePort;
import com.rivoo.appointment.infrastructure.adapter.out.rest.dto.ClientInternalDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class ClientServiceAdapter implements ClientServicePort {

    private final RestClient restClient;

    public ClientServiceAdapter(RestClient.Builder interServiceRestClientBuilder,
                                @Value("${rivoo.services.client-service.url}") String clientServiceUrl) {
        this.restClient = interServiceRestClientBuilder
                .baseUrl(clientServiceUrl)
                .build();
    }

    @Override
    public ClientInfo getClient(String tenantId, String clientExternalId) {
        log.atInfo().addKeyValue("clientId", clientExternalId).log("Fetching client from client-service");
        try {
            ClientInternalDto dto = restClient.get()
                    .uri("/api/internal/clients/{clientId}?tenantId={tenantId}", clientExternalId, tenantId)
                    .retrieve()
                    .body(ClientInternalDto.class);
            if (dto == null) {
                throw new RuntimeException("Client not found: " + clientExternalId);
            }
            return new ClientInfo(dto.id(), dto.firstName(), dto.lastName(), dto.email(), dto.phone(), dto.active());
        } catch (Exception e) {
            log.atError().setCause(e).addKeyValue("clientId", clientExternalId).log("Failed to fetch client");
            throw new RuntimeException("Failed to fetch client from client-service: " + clientExternalId, e);
        }
    }
}
