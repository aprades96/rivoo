package com.rivoo.salon.infrastructure.adapter.out.rest;

import com.rivoo.common.web.RivooHeaders;
import com.rivoo.salon.domain.port.out.StaffServicePort;
import com.rivoo.salon.infrastructure.adapter.out.rest.dto.EmployeePublicDto;
import com.rivoo.salon.infrastructure.adapter.out.rest.dto.ServiceOfferingPublicDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
public class StaffServiceAdapter implements StaffServicePort {

    private final RestClient restClient;

    public StaffServiceAdapter(RestClient.Builder interServiceRestClientBuilder,
                               @Value("${rivoo.services.staff-service.url}") String staffServiceUrl) {
        this.restClient = interServiceRestClientBuilder
                .baseUrl(staffServiceUrl)
                .build();
    }

    @Override
    public List<EmployeePublicInfo> getPublicEmployees(String tenantId) {
        List<EmployeePublicDto> employees;
        try {
            // Public booking requests are anonymous: TenantContext is empty (no JWT, no
            // tenant_id claim), so InterServiceRestClientConfig's automatic propagation of
            // X-Tenant-Id does not fire. We set it explicitly here.
            // Note: if this port were ever invoked from an authenticated flow where
            // TenantContext IS populated (e.g. an owner of tenant A calling with a
            // different tenantId), headerPropagationInterceptor unconditionally
            // overwrites this explicit header with TenantContext's value. The
            // effective tenant queried would then be the caller's own tenant, not the
            // argument passed here — fail-closed (no cross-tenant leak), but it
            // contradicts a literal reading of "this header carries the argument's
            // tenantId" for that scenario.
            // staff-service's listPublicByTenant already filters by the tenantId taken from
            // the path (explicit column filter), so the response is correct even without
            // this header. This header is defense in depth: TenantInterceptor reads it and
            // populates TenantContext, which re-activates Hibernate's tenant @Filter as a
            // second safety net for that request. If a future query on this endpoint forgets
            // the explicit filter, having the header turns that failure mode closed (returns
            // nothing) instead of open (returns every tenant's data).
            employees = restClient.get()
                    .uri("/api/internal/staff/{tenantId}/public/employees", tenantId)
                    .header(RivooHeaders.TENANT_ID, tenantId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<EmployeePublicDto>>() {});
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("targetTenantId", tenantId)
                    .log("Failed to fetch public employees from staff-service, returning empty list");
            return List.of();
        } catch (HttpClientErrorException e) {
            log.atError()
                    .setCause(e)
                    .addKeyValue("targetTenantId", tenantId)
                    .log("staff-service rejected public employees request with a client error, returning empty list");
            return List.of();
        }

        if (employees == null) {
            return List.of();
        }

        return employees.stream()
                .map(dto -> new EmployeePublicInfo(dto.id(), dto.firstName(), dto.lastName(),
                        dto.jobTitle(), dto.serviceIds()))
                .toList();
    }

    @Override
    public List<ServicePublicInfo> getPublicServices(String tenantId) {
        List<ServiceOfferingPublicDto> services;
        try {
            // See getPublicEmployees for why X-Tenant-Id is set explicitly here, and for
            // how headerPropagationInterceptor can override it for authenticated callers.
            services = restClient.get()
                    .uri("/api/internal/staff/{tenantId}/public/services", tenantId)
                    .header(RivooHeaders.TENANT_ID, tenantId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ServiceOfferingPublicDto>>() {});
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("targetTenantId", tenantId)
                    .log("Failed to fetch public services from staff-service, returning empty list");
            return List.of();
        } catch (HttpClientErrorException e) {
            log.atError()
                    .setCause(e)
                    .addKeyValue("targetTenantId", tenantId)
                    .log("staff-service rejected public services request with a client error, returning empty list");
            return List.of();
        }

        if (services == null) {
            return List.of();
        }

        return services.stream()
                .map(dto -> new ServicePublicInfo(dto.id(), dto.name(), dto.description(),
                        dto.durationMinutes(), dto.price(), dto.currency()))
                .toList();
    }
}
