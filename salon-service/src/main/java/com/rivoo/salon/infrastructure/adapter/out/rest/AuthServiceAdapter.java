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
        log.info("Calling auth-service to register owner for tenant {}", tenantId);

        RegisterOwnerRequest request = new RegisterOwnerRequest(
                tenantId, email, password, firstName, lastName, salonName, subscriptionPlan);

        try {
            RegisterOwnerResponse response = restClient.post()
                    .uri("/api/internal/auth/register-owner")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(RegisterOwnerResponse.class);

            log.info("Owner registered in Keycloak: keycloakUserId={}", response.keycloakUserId());
            return response.keycloakUserId();
        } catch (AuthServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthServiceException("Failed to register owner in auth-service for tenant: " + tenantId, e);
        }
    }

    @Override
    public void deleteUser(String keycloakUserId) {
        log.info("Calling auth-service to delete user {}", keycloakUserId);

        try {
            restClient.delete()
                    .uri("/api/internal/auth/users/{userId}", keycloakUserId)
                    .retrieve()
                    .toBodilessEntity();

            log.info("User deleted from Keycloak: {}", keycloakUserId);
        } catch (Exception e) {
            throw new AuthServiceException("Failed to delete user in auth-service: " + keycloakUserId, e);
        }
    }
}
