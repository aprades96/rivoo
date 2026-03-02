package com.rivoo.salon.infrastructure.adapter.out.rest;

import com.rivoo.salon.domain.exception.AuthServiceException;
import com.rivoo.salon.domain.port.out.AuthServicePort;
import com.rivoo.salon.infrastructure.adapter.out.rest.dto.RegisterOwnerRequest;
import com.rivoo.salon.infrastructure.adapter.out.rest.dto.RegisterOwnerResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

        try {
            RegisterOwnerResponse response = restClient.post()
                    .uri("/api/internal/auth/register-owner")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(RegisterOwnerResponse.class);

            log.atInfo().addKeyValue("keycloakUserId", response.keycloakUserId()).log("Owner registered in Keycloak");
            return response.keycloakUserId();
        } catch (AuthServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthServiceException("Failed to register owner in auth-service for tenant: " + tenantId, e);
        }
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
        } catch (Exception e) {
            throw new AuthServiceException("Failed to delete user in auth-service: " + keycloakUserId, e);
        }
    }
}
