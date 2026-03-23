package com.rivoo.admin.infrastructure.adapter.out.rest;

import com.rivoo.admin.infrastructure.adapter.out.rest.dto.TenantUsersDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AuthAdminAdapter {

    private final RestClient restClient;

    public AuthAdminAdapter(RestClient.Builder interServiceRestClientBuilder,
                            @Value("${rivoo.services.auth-service.url}") String authServiceUrl) {
        this.restClient = interServiceRestClientBuilder
                .baseUrl(authServiceUrl)
                .build();
    }

    /**
     * Calls auth-service to enable or disable all Keycloak users for a tenant.
     *
     * @param tenantId the tenant to update
     * @param enabled  true to enable, false to disable
     */
    public void setTenantEnabled(String tenantId, boolean enabled) {
        log.atInfo()
                .addKeyValue("enabled", enabled)
                .log("Calling auth-service PUT /api/internal/admin/tenants/status");

        restClient.put()
                .uri("/api/internal/admin/tenants/{tenantId}/status", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("enabled", enabled))
                .retrieve()
                .toBodilessEntity();

        log.atInfo()
                .addKeyValue("enabled", enabled)
                .log("auth-service tenant status updated");
    }

    /**
     * Lists all Keycloak users belonging to a tenant.
     * auth-service returns List<TenantUserResponse> which has {keycloakUserId, tenantId, role, active}.
     * We map it to TenantUsersDto; email/firstName/lastName are not available from the local DB endpoint.
     */
    public List<TenantUsersDto> getTenantUsers(String tenantId) {
        log.atInfo().log("Calling auth-service GET /api/internal/auth/tenants/users");

        List<Map<String, Object>> raw = restClient.get()
                .uri("/api/internal/auth/tenants/{tenantId}/users", tenantId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (raw == null) {
            return List.of();
        }

        List<TenantUsersDto> users = raw.stream()
                .map(u -> new TenantUsersDto(
                        (String) u.get("keycloakUserId"),
                        null,       // TenantUserResponse does not expose email
                        null,       // TenantUserResponse does not expose firstName
                        null,       // TenantUserResponse does not expose lastName
                        (String) u.get("role")
                ))
                .toList();

        log.atInfo().addKeyValue("count", users.size()).log("auth-service returned tenant users");
        return users;
    }
}
