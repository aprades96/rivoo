package com.rivoo.billing.infrastructure.adapter.out.rest;

import com.rivoo.billing.domain.port.out.AuthServicePort;
import com.rivoo.billing.infrastructure.adapter.out.rest.dto.UpdateTenantAttributesRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class AuthServiceAdapter implements AuthServicePort {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceAdapter.class);

    private final RestClient restClient;

    public AuthServiceAdapter(RestClient.Builder interServiceRestClientBuilder,
                              @Value("${rivoo.services.auth-service.url}") String authServiceUrl) {
        this.restClient = interServiceRestClientBuilder
                .baseUrl(authServiceUrl)
                .build();
    }

    @Override
    public void updateTenantAttributes(String tenantId, Map<String, String> attributes) {
        log.atInfo()
                .addKeyValue("attributeKeys", attributes.keySet())
                .log("Calling auth-service to update tenant attributes");

        try {
            UpdateTenantAttributesRequest request = new UpdateTenantAttributesRequest(attributes);

            restClient.put()
                    .uri("/api/internal/auth/tenants/{tenantId}/attributes", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

            log.atInfo()
                    .log("Tenant attributes updated in auth-service");
        } catch (Exception ex) {
            log.atWarn()
                    .setCause(ex)
                    .log("Failed to update tenant attributes in auth-service");
            throw new RuntimeException("Failed to update tenant attributes for tenant: " + tenantId, ex);
        }
    }
}
