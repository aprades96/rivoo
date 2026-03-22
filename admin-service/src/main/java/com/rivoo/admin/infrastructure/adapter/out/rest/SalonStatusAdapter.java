package com.rivoo.admin.infrastructure.adapter.out.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class SalonStatusAdapter {

    private final RestClient restClient;

    public SalonStatusAdapter(RestClient.Builder interServiceRestClientBuilder,
                              @Value("${rivoo.services.salon-service.url}") String salonServiceUrl) {
        this.restClient = interServiceRestClientBuilder
                .baseUrl(salonServiceUrl)
                .build();
    }

    /**
     * Updates the status of a salon (e.g., SUSPENDED or ACTIVE) in salon-service.
     *
     * @param tenantId the tenant whose salon to update
     * @param status   the new status string (e.g., "SUSPENDED", "ACTIVE")
     */
    public void updateSalonStatus(String tenantId, String status) {
        log.atInfo()
                .addKeyValue("tenantId", tenantId)
                .addKeyValue("status", status)
                .log("Calling salon-service PUT /api/internal/salons/status");

        restClient.put()
                .uri("/api/internal/salons/{tenantId}/status", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", status))
                .retrieve()
                .toBodilessEntity();

        log.atInfo()
                .addKeyValue("tenantId", tenantId)
                .addKeyValue("status", status)
                .log("salon-service salon status updated");
    }
}
