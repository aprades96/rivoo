package com.rivoo.salon.infrastructure.adapter.out.rest;

import com.rivoo.salon.domain.exception.BillingServiceException;
import com.rivoo.salon.domain.port.out.BillingServicePort;
import com.rivoo.salon.infrastructure.adapter.out.rest.dto.CreateSubscriptionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class BillingServiceAdapter implements BillingServicePort {

    private final RestClient restClient;

    public BillingServiceAdapter(RestClient.Builder interServiceRestClientBuilder,
                                 @Value("${rivoo.services.billing-service.url}") String billingServiceUrl) {
        this.restClient = interServiceRestClientBuilder
                .baseUrl(billingServiceUrl)
                .build();
    }

    @Override
    public void createSubscription(String tenantId, String ownerEmail, String salonName) {
        log.atInfo().addKeyValue("salonName", salonName).log("Calling billing-service to create subscription");

        CreateSubscriptionRequest request = new CreateSubscriptionRequest(tenantId, ownerEmail, salonName);

        try {
            restClient.post()
                    .uri("/api/internal/billing/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

            log.atInfo().addKeyValue("salonName", salonName).log("Subscription created in billing-service");
        } catch (Exception e) {
            throw new BillingServiceException(
                    "Failed to create subscription in billing-service for tenant: " + tenantId, e);
        }
    }
}
