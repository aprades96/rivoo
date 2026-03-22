package com.rivoo.billing.infrastructure.adapter.out.rest;

import com.rivoo.billing.domain.port.out.SalonServicePort;
import com.rivoo.billing.infrastructure.adapter.out.rest.dto.UpdateSalonStatusRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SalonServiceAdapter implements SalonServicePort {

    private static final Logger log = LoggerFactory.getLogger(SalonServiceAdapter.class);

    private final RestClient restClient;

    public SalonServiceAdapter(RestClient.Builder interServiceRestClientBuilder,
                               @Value("${rivoo.services.salon-service.url}") String salonServiceUrl) {
        this.restClient = interServiceRestClientBuilder
                .baseUrl(salonServiceUrl)
                .build();
    }

    @Override
    public void updateSalonStatus(String tenantId, String status) {
        log.atInfo()
                .addKeyValue("tenantId", tenantId)
                .addKeyValue("status", status)
                .log("Calling salon-service to update salon status");

        try {
            UpdateSalonStatusRequest request = new UpdateSalonStatusRequest(status);

            restClient.put()
                    .uri("/api/internal/salons/{tenantId}/status", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

            log.atInfo()
                    .addKeyValue("tenantId", tenantId)
                    .addKeyValue("status", status)
                    .log("Salon status updated in salon-service");
        } catch (Exception ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("tenantId", tenantId)
                    .addKeyValue("status", status)
                    .log("Failed to update salon status in salon-service");
            throw new RuntimeException("Failed to update salon status for tenant: " + tenantId, ex);
        }
    }
}
