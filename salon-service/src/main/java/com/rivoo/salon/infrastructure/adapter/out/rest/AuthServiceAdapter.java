package com.rivoo.salon.infrastructure.adapter.out.rest;

import com.rivoo.salon.domain.exception.AuthServiceException;
import com.rivoo.salon.domain.port.out.AuthServicePort;
import com.rivoo.salon.infrastructure.adapter.out.rest.dto.RegisterOwnerRequest;
import com.rivoo.salon.infrastructure.adapter.out.rest.dto.RegisterOwnerResponse;
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
public class AuthServiceAdapter implements AuthServicePort {

    private final RestClient restClient;

    public AuthServiceAdapter(RestClient.Builder interServiceRestClientBuilder,
                              @Value("${rivoo.services.auth-service.url}") String authServiceUrl) {
        this.restClient = interServiceRestClientBuilder
                .baseUrl(authServiceUrl)
                .build();
    }

    @Override
    public String registerOwner(String tenantId, String email, String password,
                                String firstName, String lastName, String salonName,
                                String subscriptionPlan) {
        log.atInfo().log("Calling auth-service to register owner");

        RegisterOwnerRequest request = new RegisterOwnerRequest(
                tenantId, email, password, firstName, lastName, salonName, subscriptionPlan);

        RegisterOwnerResponse response;
        try {
            response = restClient.post()
                    .uri("/api/internal/auth/register-owner")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(RegisterOwnerResponse.class);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            // auth-service is genuinely broken (it blew up, or we never reached it). Only this
            // family is a real 502: the upstream never got to answer the question.
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("targetTenantId", tenantId)
                    .log("auth-service failed while registering the owner");
            throw AuthServiceException.unavailable(
                    "Failed to register owner in auth-service for tenant: " + tenantId, e);
        } catch (HttpClientErrorException e) {
            // auth-service worked and refused the request (409 for an email already in Keycloak,
            // 400 for a password its policy rejects, ...). Flattening that into a 502 claimed the
            // dependency was down, hid the real reason from the caller, and paged an operator over
            // a healthy service. It surfaces as the business rejection it is (422).
            log.atError()
                    .setCause(e)
                    .addKeyValue("targetTenantId", tenantId)
                    .addKeyValue("upstreamStatus", e.getStatusCode().value())
                    .log("auth-service rejected the owner registration with a client error");
            throw AuthServiceException.rejected(
                    "auth-service rejected the owner registration for tenant: " + tenantId, e);
        } catch (RestClientException e) {
            // Final safety net, kept deliberately: the catches above CLASSIFY, they never replace
            // it. Unreadable 2xx bodies (UnknownContentTypeException), truncated bodies and
            // DTO-shape mismatches during a rolling deploy all land here. Removing this catch is
            // the exact change that once shipped a production 500 from this package.
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("targetTenantId", tenantId)
                    .log("Could not complete the auth-service owner registration call");
            throw AuthServiceException.unavailable(
                    "Failed to register owner in auth-service for tenant: " + tenantId, e);
        }

        if (response == null || response.keycloakUserId() == null) {
            // A 2xx with no usable body is not "the owner was created": it is an absent/unreadable
            // response, same family as the RestClientException case above. Without this guard the
            // saga would happily store a null ownerUserId (or NPE into a blanket 500).
            log.atWarn()
                    .addKeyValue("targetTenantId", tenantId)
                    .log("auth-service returned a 2xx without a usable keycloakUserId");
            throw AuthServiceException.unavailable(
                    "Failed to register owner in auth-service for tenant: " + tenantId, null);
        }

        log.atInfo().addKeyValue("keycloakUserId", response.keycloakUserId()).log("Owner registered in Keycloak");
        return response.keycloakUserId();
    }

    @Override
    public void deleteUser(String keycloakUserId) {
        log.atInfo().addKeyValue("keycloakUserId", keycloakUserId).log("Calling auth-service to delete user");

        try {
            restClient.delete()
                    .uri("/api/internal/auth/users/{userId}", keycloakUserId)
                    .retrieve()
                    .toBodilessEntity();

            log.atInfo().addKeyValue("keycloakUserId", keycloakUserId).log("User deleted from Keycloak");
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("keycloakUserId", keycloakUserId)
                    .log("auth-service failed while deleting the user");
            throw AuthServiceException.unavailable(
                    "Failed to delete user in auth-service: " + keycloakUserId, e);
        } catch (HttpClientErrorException e) {
            // Same classification as registerOwner: a 4xx here is auth-service answering, not
            // auth-service being down. It is reached from the saga's compensation path, which
            // swallows it — but the status it carries is what an operator's alerting keys on, so
            // it must not claim an outage that is not happening.
            log.atError()
                    .setCause(e)
                    .addKeyValue("keycloakUserId", keycloakUserId)
                    .addKeyValue("upstreamStatus", e.getStatusCode().value())
                    .log("auth-service rejected the user deletion with a client error");
            throw AuthServiceException.rejected(
                    "auth-service rejected the deletion of user: " + keycloakUserId, e);
        } catch (RestClientException e) {
            // Final safety net — see registerOwner.
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("keycloakUserId", keycloakUserId)
                    .log("Could not complete the auth-service user deletion call");
            throw AuthServiceException.unavailable(
                    "Failed to delete user in auth-service: " + keycloakUserId, e);
        }
    }
}
