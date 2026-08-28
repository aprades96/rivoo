package com.rivoo.salon.infrastructure.adapter.out.rest;

import com.rivoo.salon.domain.exception.BillingServiceException;
import com.rivoo.salon.domain.port.out.BillingServicePort;
import com.rivoo.salon.infrastructure.adapter.out.rest.dto.CreateSubscriptionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
        } catch (HttpServerErrorException | ResourceAccessException e) {
            // billing-service is genuinely broken (it blew up, or we never reached it). This is
            // the only family that deserves a 502: the upstream did not get to answer.
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("targetTenantId", tenantId)
                    .log("billing-service failed while creating the subscription");
            throw BillingServiceException.unavailable(
                    "Failed to create subscription in billing-service for tenant: " + tenantId, e);
        } catch (HttpClientErrorException e) {
            // The opposite case: billing-service worked and refused the request (422 for a tenant
            // that already has a subscription, 400 for a payload it will not accept, ...). Folding
            // this into a 502 told the caller "the upstream is broken" and paged an operator over
            // a working dependency. It is a business rejection, so it surfaces as one (422).
            log.atError()
                    .setCause(e)
                    .addKeyValue("targetTenantId", tenantId)
                    .addKeyValue("upstreamStatus", e.getStatusCode().value())
                    .log("billing-service rejected the subscription request with a client error");
            throw BillingServiceException.rejected(
                    "billing-service rejected the subscription request for tenant: " + tenantId, e);
        } catch (RestClientException e) {
            // Final safety net, kept deliberately: the two catches above CLASSIFY, they do not
            // replace it. Everything else RestClient can raise lands here — an unreadable 2xx body
            // (UnknownContentTypeException from an edge/proxy error page), a body cut short
            // mid-stream, a shape mismatch during a rolling deploy. Dropping this catch is exactly
            // the change that once shipped a production 500 from this package. Not classifiable as
            // "them saying no", so it is treated like the broken-dependency case.
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("targetTenantId", tenantId)
                    .log("Could not complete the billing-service subscription call");
            throw BillingServiceException.unavailable(
                    "Failed to create subscription in billing-service for tenant: " + tenantId, e);
        }
    }
}
