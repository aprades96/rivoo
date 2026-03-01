package com.rivoo.staff.infrastructure.adapter.out.rest;

import com.rivoo.staff.domain.exception.AuthServiceException;
import com.rivoo.staff.domain.port.out.AuthServicePort;
import com.rivoo.staff.infrastructure.adapter.out.rest.dto.RegisterEmployeeRestRequest;
import com.rivoo.staff.infrastructure.adapter.out.rest.dto.RegisterEmployeeRestResponse;
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
    public String registerEmployee(String tenantId, String email, String password,
                                   String firstName, String lastName, String salonName) {
        log.info("Calling auth-service to register employee for tenant {}", tenantId);

        RegisterEmployeeRestRequest request = new RegisterEmployeeRestRequest(
                tenantId, email, password, firstName, lastName, salonName);

        try {
            RegisterEmployeeRestResponse response = restClient.post()
                    .uri("/api/internal/auth/register-employee")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(RegisterEmployeeRestResponse.class);

            log.info("Employee registered in Keycloak: keycloakUserId={}", response.keycloakUserId());
            return response.keycloakUserId();
        } catch (AuthServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthServiceException("Failed to register employee in auth-service for tenant: " + tenantId, e);
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
