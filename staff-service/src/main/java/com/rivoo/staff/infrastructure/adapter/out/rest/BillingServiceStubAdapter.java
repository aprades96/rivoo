package com.rivoo.staff.infrastructure.adapter.out.rest;

import com.rivoo.staff.domain.port.out.BillingServicePort;
import com.rivoo.staff.infrastructure.adapter.out.rest.dto.PlanLimitsDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class BillingServiceStubAdapter implements BillingServicePort {

    private final RestClient restClient;

    public BillingServiceStubAdapter(RestClient.Builder interServiceRestClientBuilder,
                                     @Value("${rivoo.services.billing-service.url}") String billingServiceUrl) {
        this.restClient = interServiceRestClientBuilder
                .baseUrl(billingServiceUrl)
                .build();
    }

    @Override
    public int getMaxEmployees(String tenantId) {
        log.atInfo().addKeyValue("tenantId", tenantId).log("Calling billing-service for plan limits (write operation)");

        try {
            PlanLimitsDto limits = restClient.get()
                    .uri("/api/internal/billing/tenants/{tenantId}/plan-limits?forWriteOperation=true", tenantId)
                    .retrieve()
                    .body(PlanLimitsDto.class);

            if (limits == null) {
                log.atWarn().addKeyValue("tenantId", tenantId).log("Billing-service returned null plan limits, falling back to unlimited");
                return -1;
            }

            log.atInfo()
                    .addKeyValue("tenantId", tenantId)
                    .addKeyValue("planName", limits.planName())
                    .addKeyValue("maxEmployees", limits.maxEmployees())
                    .log("Plan limits retrieved from billing-service");

            return limits.maxEmployees();
        } catch (Exception e) {
            log.atWarn().setCause(e).addKeyValue("tenantId", tenantId)
                    .log("Failed to fetch plan limits from billing-service, falling back to unlimited");
            return -1;
        }
    }
}
