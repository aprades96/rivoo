package com.rivoo.staff.infrastructure.adapter.out.rest;

import com.rivoo.staff.domain.exception.AuthServiceException;
import com.rivoo.staff.domain.port.out.AuthServicePort;
import com.rivoo.staff.infrastructure.adapter.out.rest.dto.RegisterEmployeeRestRequest;
import com.rivoo.staff.infrastructure.adapter.out.rest.dto.RegisterEmployeeRestResponse;
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
    public String registerEmployee(String tenantId, String email, String password,
                                   String firstName, String lastName, String salonName) {
        log.atInfo().log("Calling auth-service to register employee");

        RegisterEmployeeRestRequest request = new RegisterEmployeeRestRequest(
                tenantId, email, password, firstName, lastName, salonName);

        RegisterEmployeeRestResponse response;
        try {
            response = restClient.post()
                    .uri("/api/internal/auth/register-employee")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(RegisterEmployeeRestResponse.class);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            // auth-service is genuinely broken (it blew up, or we never reached it). Only this
            // family is a real 502: the upstream never got to answer the question.
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("targetTenantId", tenantId)
                    .log("auth-service failed while registering the employee");
            throw AuthServiceException.unavailable(
                    "Failed to register employee in auth-service for tenant: " + tenantId, e);
        } catch (HttpClientErrorException e) {
            // auth-service worked and refused the request (409 for an email already in Keycloak,
            // 400 for a password its policy rejects, ...). Flattening that into a 502 claimed the
            // dependency was down, hid the real reason from the salon owner filling the employee
            // form, and paged an operator over a healthy service. It surfaces as the business
            // rejection it is (422).
            log.atError()
                    .setCause(e)
                    .addKeyValue("targetTenantId", tenantId)
                    .addKeyValue("upstreamStatus", e.getStatusCode().value())
                    .log("auth-service rejected the employee registration with a client error");
            throw AuthServiceException.rejected(
                    "auth-service rejected the employee registration for tenant: " + tenantId, e);
        } catch (RestClientException e) {
            // Final safety net, kept deliberately: the catches above CLASSIFY, they never replace
            // it. Unreadable 2xx bodies (UnknownContentTypeException), truncated bodies and
            // DTO-shape mismatches during a rolling deploy all land here. Removing this catch is
            // the exact change that once shipped a production 500 from this package.
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("targetTenantId", tenantId)
                    .log("Could not complete the auth-service employee registration call");
            throw AuthServiceException.unavailable(
                    "Failed to register employee in auth-service for tenant: " + tenantId, e);
        }

        if (response == null || response.keycloakUserId() == null) {
            // A 2xx with no usable body is not "the employee account was created": it is an
            // absent/unreadable response, same family as the RestClientException case above.
            // This guard is what the old blanket catch was silently absorbing: an empty 2xx body
            // NPE'd on response.keycloakUserId() and came out as a 502 by accident, and a `{}`
            // body did not even fail — EmployeeService.create stored a null keycloakUserId and
            // reported success for an employee that has no account to log in with.
            log.atWarn()
                    .addKeyValue("targetTenantId", tenantId)
                    .log("auth-service returned a 2xx without a usable keycloakUserId");
            throw AuthServiceException.unavailable(
                    "Failed to register employee in auth-service for tenant: " + tenantId, null);
        }

        log.atInfo().addKeyValue("keycloakUserId", response.keycloakUserId()).log("Employee registered in Keycloak");
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
            // Same classification as registerEmployee: a 4xx here is auth-service answering, not
            // auth-service being down (a 404 for an already-removed user is the common case), so
            // it must not claim an outage that is not happening.
            log.atError()
                    .setCause(e)
                    .addKeyValue("keycloakUserId", keycloakUserId)
                    .addKeyValue("upstreamStatus", e.getStatusCode().value())
                    .log("auth-service rejected the user deletion with a client error");
            throw AuthServiceException.rejected(
                    "auth-service rejected the deletion of user: " + keycloakUserId, e);
        } catch (RestClientException e) {
            // Final safety net — see registerEmployee.
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("keycloakUserId", keycloakUserId)
                    .log("Could not complete the auth-service user deletion call");
            throw AuthServiceException.unavailable(
                    "Failed to delete user in auth-service: " + keycloakUserId, e);
        }
    }
}
